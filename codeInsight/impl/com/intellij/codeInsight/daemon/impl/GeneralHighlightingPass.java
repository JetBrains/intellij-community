package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.injected.DocumentWindow;
import com.intellij.openapi.editor.impl.injected.VirtualFileWindow;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneralHighlightingPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass");
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png");
  private static final Icon IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/gutter/implementingMethod.png");

  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;

  private volatile Collection<HighlightInfo> myHighlights = Collections.emptyList();
  private volatile Map<TextRange,Collection<HighlightInfo>> myInjectedPsiHighlights = new THashMap<TextRange, Collection<HighlightInfo>>();
  private volatile Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  private final DaemonCodeAnalyzerSettings mySettings = DaemonCodeAnalyzerSettings.getInstance();
  protected boolean myHasErrorElement;
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.syntax");

  public GeneralHighlightingPass(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Document document,
                                 int startOffset,
                                 int endOffset,
                                 boolean updateAll) {
    super(project, document, IN_PROGRESS_ICON, PRESENTABLE_NAME);
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;

    LOG.assertTrue(myFile.isValid());
    setId(Pass.UPDATE_ALL);
  }

  private static final Key<AtomicInteger> HIGHLIGHT_VISITOR_INSTANCE_COUNT = new Key<AtomicInteger>("HIGHLIGHT_VISITOR_INSTANCE_COUNT");
  private HighlightVisitor[] createHighlightVisitors() {
    AtomicInteger count = myProject.getUserData(HIGHLIGHT_VISITOR_INSTANCE_COUNT);
    if (count == null) {
      count = ((UserDataHolderEx)myProject).putUserDataIfAbsent(HIGHLIGHT_VISITOR_INSTANCE_COUNT, new AtomicInteger(0));
    }
    HighlightVisitor[] highlightVisitors = Extensions.getExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, myProject);
    if (count.getAndIncrement() != 0) {
      highlightVisitors = highlightVisitors.clone();
      for (int i = 0; i < highlightVisitors.length; i++) {
        HighlightVisitor highlightVisitor = highlightVisitors[i];
        highlightVisitors[i] = highlightVisitor.clone();
      }
    }
    for (final HighlightVisitor highlightVisitor : highlightVisitors) {
      highlightVisitor.init();
    }
    return highlightVisitors;
  }

  private void releaseHighlightVisitors(HighlightVisitor[] highlightVisitors) {
    for (HighlightVisitor visitor : highlightVisitors) {
      if (visitor instanceof Disposable) {
        ((Disposable)visitor).dispose();
      }
    }
    AtomicInteger count = myProject.getUserData(HIGHLIGHT_VISITOR_INSTANCE_COUNT);
    int i = count.decrementAndGet();
    assert i >=0 : i;
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    final HighlightVisitor[] highlightVisitors = createHighlightVisitors();
    final Collection<HighlightInfo> result = new THashSet<HighlightInfo>(100);
    final List<LineMarkerInfo> lineMarkers = new ArrayList<LineMarkerInfo>();
    try {
      final RefCountHolder refCountHolder;
      if (myUpdateAll) {
        DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
        refCountHolder = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().getRefCountHolder(myFile);
      }
      else {
        refCountHolder = null;
      }
      setRefCountHolders(refCountHolder, highlightVisitors);

      Runnable doCollectInfo = new Runnable() {
        public void run() {
          if (refCountHolder != null) {
            DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
            PsiElement dirtyScope = daemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(myDocument, Pass.UPDATE_ALL);
            if (dirtyScope != null) {
              if (dirtyScope instanceof PsiFile) {
                refCountHolder.clear();
              }
              else {
                refCountHolder.removeInvalidRefs();
              }
            }
          }

          final FileViewProvider viewProvider = myFile.getViewProvider();
          final Set<Language> relevantLanguages = viewProvider.getPrimaryLanguages();
          for (Language language : relevantLanguages) {
            PsiElement psiRoot = viewProvider.getPsi(language);
            if (!HighlightUtil.shouldHighlight(psiRoot)) continue;
            //long time = System.currentTimeMillis();
            List<PsiElement> elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
            if (elements.isEmpty()) {
              elements = Collections.singletonList(psiRoot);
            }
            //LOG.debug("Elements collected for: " + (System.currentTimeMillis() - time) / 1000.0 + "s");
            //time = System.currentTimeMillis();

            addLineMarkers(elements, lineMarkers);
            //LOG.debug("Line markers collected for: " + (System.currentTimeMillis() - time) / 1000.0 + "s");

            result.addAll(collectHighlights(elements, highlightVisitors));
            result.addAll(highlightTodos());
            myInjectedPsiHighlights = highlightInjectedPsi(elements);
          }
        }
      };
      if (refCountHolder != null) {
        refCountHolder.analyzeAndStoreReferences(doCollectInfo, progress);
      }
      else {
        doCollectInfo.run();
      }
    }
    finally {
      setRefCountHolders(null, highlightVisitors);
      releaseHighlightVisitors(highlightVisitors);
    }
    myHighlights = result;
    myMarkers = lineMarkers;
  }

  private Map<TextRange,Collection<HighlightInfo>> highlightInjectedPsi(final List<PsiElement> elements) {
    Collection<PsiElement> hosts = new THashSet<PsiElement>();
    List<DocumentWindow> injected = InjectedLanguageUtil.getCachedInjectedDocuments(getDocument());

    // rehighlight all injected PSI regardless the range,
    // since change in one place can lead to invalidation of injected PSI in (completely) other place.
    for (DocumentWindow documentRange : injected) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(documentRange);
      if (file == null) continue;
      PsiElement context = file.getContext();
      if (context != null
          && context.isValid()
          && (myUpdateAll || new TextRange(myStartOffset, myEndOffset).contains(context.getTextRange()))) {
        hosts.add(context);
      }
    }
    hosts.addAll(elements);

    final Collection<PsiFile> injectedFiles = new THashSet<PsiFile>();
    final AnnotationHolderImpl annotationHolder = createAnnotationHolder();
    final Map<TextRange,Collection<HighlightInfo>> result = new THashMap<TextRange, Collection<HighlightInfo>>(hosts.size());
    for (PsiElement element : hosts) {
      InjectedLanguageUtil.enumerate(element, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          injectedFiles.add(injectedPsi);
        }
      }, false);
    }

    for (PsiFile injectedPsi : injectedFiles) {
      highlightInjectedIn(injectedPsi, annotationHolder);
      DocumentWindow documentWindow = (DocumentWindow)PsiDocumentManager.getInstance(myProject).getCachedDocument(injectedPsi);
      for (Annotation annotation : annotationHolder) {
        HighlightInfo highlightInfo = HighlightUtil.convertToHighlightInfo(annotation);
        TextRange textRange = documentWindow.getHostRange(highlightInfo.startOffset);
        Collection<HighlightInfo> infos = result.get(textRange);
        if (infos == null) {
          infos = new ArrayList<HighlightInfo>(10);
          result.put(textRange, infos);
        }
        infos.add(highlightInfo);
      }
      annotationHolder.clear();
    }

    return result;
  }

  private static void highlightInjectedIn(PsiFile injectedPsi, final AnnotationHolderImpl annotationHolder) {
    final DocumentWindow documentRange = ((VirtualFileWindow)injectedPsi.getContainingFile().getViewProvider().getVirtualFile()).getDocumentWindow();
    assert documentRange != null;
    assert documentRange.getText().equals(injectedPsi.getText());
    LanguageDialect languageDialect = injectedPsi.getLanguageDialect();
    Language injectedLanguage = languageDialect == null ? injectedPsi.getLanguage() : languageDialect;
    final List<Annotator> annotators = injectedLanguage.getAnnotators();

    final AnnotationHolderImpl fixingOffsetsHolder = new AnnotationHolderImpl() {
      public boolean add(final Annotation annotation) {
        return true; // we are going to hand off the annotation to the annotationHolder anyway
      }

      protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
        TextRange editable = documentRange.intersectWithEditable(range);
        boolean shouldHighlight = editable != null;
        if (editable == null) editable = new TextRange(0,0);
        TextRange patched = documentRange.injectedToHost(editable);
        Annotation annotation = super.createAnnotation(patched, severity, message);
        if (shouldHighlight) { //do not highlight generated header/footer
          annotationHolder.add(annotation);
        }
        return annotation;
      }
    };
    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < annotators.size(); i++) {
          Annotator annotator = annotators.get(i);
          annotator.annotate(element, fixingOffsetsHolder);
        }
      }

      public void visitErrorElement(PsiErrorElement element) {
        HighlightInfo info = HighlightVisitorImpl.createErrorElementInfo(element);
        TextRange editable = documentRange.intersectWithEditable(new TextRange(info.startOffset, info.endOffset));
        if (editable==null) return; //do not highlight generated header/footer
        Annotation annotation = fixingOffsetsHolder.createErrorAnnotation(editable, info.description);
        annotation.setTooltip(info.toolTip);
      }
    };

    injectedPsi.accept(visitor);
    highlightInjectedSyntax(injectedLanguage, injectedPsi, annotationHolder);
  }

  private static void highlightInjectedSyntax(final Language injectedLanguage, final PsiFile injectedPsi, final AnnotationHolderImpl annotationHolder) {
    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = InjectedLanguageUtil.getHighlightTokens(injectedPsi);
    if (tokens == null) return;

    SyntaxHighlighter syntaxHighlighter = injectedLanguage.getSyntaxHighlighter(injectedPsi.getProject(), injectedPsi.getVirtualFile());
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes defaultAttrs = globalScheme.getAttributes(HighlighterColors.TEXT);

    for (Trinity<IElementType, PsiLanguageInjectionHost, TextRange> token : tokens) {
      IElementType tokenType = token.getFirst();
      PsiLanguageInjectionHost injectionHost = token.getSecond();
      TextRange textRange = token.getThird();
      TextAttributesKey[] keys = syntaxHighlighter.getTokenHighlights(tokenType);
      if (textRange.getLength() == 0) continue;

      Annotation annotation = annotationHolder.createInfoAnnotation(textRange.shiftRight(injectionHost.getTextRange().getStartOffset()), null);
      if (annotation == null) continue; // maybe out of highlightable range
      // force attribute colors to override host' ones

      TextAttributes attributes = keys.length == 0 ? null : globalScheme.getAttributes(keys[0]);
      if (attributes == null || attributes.isEmpty() || attributes.equals(defaultAttrs)) {
        annotation.setEnforcedTextAttributes(TextAttributes.ERASE_MARKER);
      }
      else {
        Color back = attributes.getBackgroundColor() == null ? globalScheme.getDefaultBackground() : attributes.getBackgroundColor();
        Color fore = attributes.getForegroundColor() == null ? globalScheme.getDefaultForeground() : attributes.getForegroundColor();
        TextAttributes forced = new TextAttributes(fore, back, attributes.getEffectColor(), attributes.getEffectType(), attributes.getFontType());
        annotation.setEnforcedTextAttributes(forced);
      }
    }
  }

  private static void setRefCountHolders(RefCountHolder refCountHolder, final HighlightVisitor[] visitors) {
    for (HighlightVisitor visitor : visitors) {
      visitor.setRefCountHolder(refCountHolder);
    }
  }

  protected void applyInformationWithProgress() {
    myFile.putUserData(HAS_ERROR_ELEMENT, myHasErrorElement);

    UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myMarkers, Pass.UPDATE_ALL);

    // highlights from both passes should be in the same layer 
    TextRange range = new TextRange(myStartOffset, myEndOffset);
    Collection<HighlightInfo> collection = myInjectedPsiHighlights.get(range);
    if (collection == null) {
      collection = new ArrayList<HighlightInfo>();
    }
    collection.addAll(myHighlights);
    myInjectedPsiHighlights.put(range, collection);
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myInjectedPsiHighlights, Pass.UPDATE_ALL);
  }

  public Collection<LineMarkerInfo> queryLineMarkers() {
    try {
      if (myFile.getNode() == null) {
        // binary file? see IDEADEV-2809
        return Collections.emptyList();
      }
      ArrayList<LineMarkerInfo> result = new ArrayList<LineMarkerInfo>();
      addLineMarkers(CodeInsightUtil.getElementsInRange(myFile, myStartOffset, myEndOffset), result);
      return result;
    }
    catch (ProcessCanceledException e) {
      return null;
    }
  }

  @NotNull
  public Collection<HighlightInfo> getHighlights() {
    ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>(myHighlights);
    for (Collection<HighlightInfo> infos : myInjectedPsiHighlights.values()) {
      list.addAll(infos);
    }
    return list;
  }

  private Collection<HighlightInfo> collectHighlights(final List<PsiElement> elements, final HighlightVisitor[] highlightVisitors) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Set<PsiElement> skipParentsSet = new THashSet<PsiElement>();
    final Set<HighlightInfo> gotHighlights = new THashSet<HighlightInfo>();
    //long totalTime = 0;
    //if (LOG.isDebugEnabled()) {
    //  totalTime = System.currentTimeMillis();
    //}

    final List<HighlightVisitor> visitors = new ArrayList<HighlightVisitor>();
    for (HighlightVisitor visitor : highlightVisitors) {
      if (visitor.suitableForFile(myFile)) visitors.add(visitor);
    }

    final boolean isAntFile = CodeInsightUtil.isAntFile(myFile);

    final HighlightInfoHolder holder = createInfoHolder();
    holder.setWritable(true);
    ProgressManager progressManager = ProgressManager.getInstance();
    setProgressLimit((long)elements.size() * visitors.size());

    int chunkSize = Math.max(1, elements.size() / 100); // one percent precision is enough
    int nextLimit = chunkSize;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      progressManager.checkCanceled();

      if (element != myFile && !skipParentsSet.isEmpty() && element.getFirstChild() != null && skipParentsSet.remove(element)) {
        skipParentsSet.add(element.getParent());
        continue;
      }

      if (element instanceof PsiErrorElement) {
        myHasErrorElement = true;
      }
      holder.clear();
      //noinspection ForLoopReplaceableByForEach
      for (int j = 0; j < visitors.size(); j++) {
        HighlightVisitor visitor = visitors.get(j);
        visitor.visit(element, holder);
      }
      if (i == nextLimit) {
        advanceProgress(chunkSize * visitors.size());
        nextLimit = i + chunkSize;
      }

      //noinspection ForLoopReplaceableByForEach
      for (int j = 0; j < holder.size(); j++) {
        HighlightInfo info = holder.get(j);
        // have to filter out already obtained highlights
        if (!gotHighlights.add(info)) continue;
        if (!isAntFile && info.getSeverity() == HighlightSeverity.ERROR) {
          skipParentsSet.add(element.getParent());
        }
      }
    }

    return gotHighlights;
  }

  protected HighlightInfoHolder createInfoHolder() {
    final HighlightInfoFilter[] filters = ApplicationManager.getApplication().getExtensions(HighlightInfoFilter.EXTENSION_POINT_NAME);
    return new HighlightInfoHolder(myFile, filters);
  }
  protected AnnotationHolderImpl createAnnotationHolder() {
    return new AnnotationHolderImpl();
  }

  private Collection<HighlightInfo> highlightTodos() {
    PsiManager psiManager = myFile.getManager();
    PsiSearchHelper helper = psiManager.getSearchHelper();
    TodoItem[] todoItems = helper.findTodoItems(myFile, myStartOffset, myEndOffset);
    if (todoItems.length == 0) return Collections.emptyList();

    List<HighlightInfo> list = new ArrayList<HighlightInfo>(todoItems.length);
    for (TodoItem todoItem : todoItems) {
      TextRange range = todoItem.getTextRange();
      String description = myDocument.getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      TextAttributes attributes = todoItem.getPattern().getAttributes().getTextAttributes();
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.TODO, range, description, attributes);
      list.add(info);
    }
    return list;
  }

  private void addLineMarkers(List<PsiElement> elements, List<LineMarkerInfo> result) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      LineMarkerInfo info = getLineMarkerInfo(element);
      if (info != null) {
        result.add(info);
      }
    }
  }

  @Nullable
  private LineMarkerInfo getLineMarkerInfo(PsiElement element) {
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element.getParent();
      int offset = element.getTextRange().getStartOffset();
      MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superSignature != null) {
        boolean overrides =
          method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

        return new LineMarkerInfo(LineMarkerInfo.MarkerType.OVERRIDING_METHOD, method, offset,
                                  overrides ? OVERRIDING_METHOD_ICON : IMPLEMENTING_METHOD_ICON);
      }
    }

    if (mySettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
      PsiElement element1 = element;
      boolean isMember = false;
      while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
        element1 = element1.getParent();
        if (element1 instanceof PsiMember) {
          isMember = true;
          break;
        }
      }
      if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
        boolean drawSeparator = false;
        int category = getCategory(element1);
        for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
          int category1 = getCategory(child);
          if (category1 == 0) continue;
          drawSeparator = category != 1 || category1 != 1;
          break;
        }

        if (drawSeparator) {
          LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.MarkerType.METHOD_SEPARATOR, element, element.getTextRange().getStartOffset(), null);
          EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return null;
  }

  private static int getCategory(PsiElement element) {
    if (element instanceof PsiField) return 1;
    if (element instanceof PsiClass || element instanceof PsiClassInitializer) return 2;
    if (element instanceof PsiMethod) {
      if (((PsiMethod)element).hasModifierProperty(PsiModifier.ABSTRACT)) {
        return 1;
      }
      String text = element.getText();
      if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
        return 1;
      }
      else {
        return 2;
      }
    }
    return 0;
  }

  private static final Key<Boolean> HAS_ERROR_ELEMENT = Key.create("HAS_ERROR_ELEMENT");

  static void reportErrorsToWolf(final HighlightInfo[] infos, @NotNull PsiFile psiFile) {
    if (!psiFile.getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    Project project = psiFile.getProject();
    if (!PsiManager.getInstance(project).isInProject(psiFile)) return; // do not report problems in libraries
    VirtualFile file = psiFile.getVirtualFile();
    if (file == null || CompilerManager.getInstance(project).isExcludedFromCompilation(file)) return;

    boolean hasErrorElement = Boolean.TRUE.equals(psiFile.getUserData(HAS_ERROR_ELEMENT));
    List<Problem> problems = convertToProblems(infos, file, hasErrorElement);
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);

    wolf.reportProblems(file, problems);
  }

  static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/errorsInProgress.png");

  public double getProgress() {
    // do not show progress of visible highlighters update
    return myUpdateAll ? super.getProgress() : -1;
  }

  private static List<Problem> convertToProblems(final HighlightInfo[] infos, final VirtualFile file,
                                                 final boolean hasErrorElement) {
    List<Problem> problems = new SmartList<Problem>();
    for (HighlightInfo info : infos) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        Problem problem = new ProblemImpl(file, info, hasErrorElement);
        problems.add(problem);
      }
    }
    return problems;
  }
}
