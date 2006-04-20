package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.problems.ProblemsToolWindow;
import com.intellij.javaee.ejb.role.EjbImplMethodRole;
import com.intellij.javaee.ejb.role.EjbMethodRole;
import com.intellij.javaee.ejb.role.EjbRolesUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.ui.MessageCategory;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class GeneralHighlightingPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass");
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png");
  private static final Icon IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/gutter/implementingMethod.png");

  private final Project myProject;
  private final PsiFile myFile;
  private final Document myDocument;
  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;
  private final boolean myCompiled;

  @NotNull private final HighlightVisitor[] myHighlightVisitors;

  private Collection<HighlightInfo> myHighlights = Collections.emptyList();
  private Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  private final DaemonCodeAnalyzerSettings mySettings = DaemonCodeAnalyzerSettings.getInstance();

  public GeneralHighlightingPass(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Document document,
                                 int startOffset,
                                 int endOffset,
                                 boolean isCompiled,
                                 boolean updateAll) {
    super(document);
    myProject = project;
    myFile = file;
    myDocument = document;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;
    myCompiled = isCompiled;

    myHighlightVisitors = createHighlightVisitors();
    LOG.assertTrue(myFile.isValid());
  }

  private static final Key<Integer> HIGHLIGHT_VISITOR_THREADS_IN_USE = new Key<Integer>("HIGHLIGHT_VISITORS_POOL");
  private HighlightVisitor[] createHighlightVisitors() {
    HighlightVisitor[] highlightVisitors;
    synchronized(myProject) {
      Integer num = myProject.getUserData(HIGHLIGHT_VISITOR_THREADS_IN_USE);
      highlightVisitors = myProject.getComponents(HighlightVisitor.class);
      if (num == null) {
        num = 0;
      }
      else {
        highlightVisitors = highlightVisitors.clone();
        for (int i = 0; i < highlightVisitors.length; i++) {
          HighlightVisitor highlightVisitor = highlightVisitors[i];
          highlightVisitors[i] = highlightVisitor.clone();
        }
      }
      myProject.putUserData(HIGHLIGHT_VISITOR_THREADS_IN_USE, num+1);
    }
    for (final HighlightVisitor highlightVisitor : highlightVisitors) {
      highlightVisitor.init();
    }
    return highlightVisitors;
  }
  private void releaseHighlightVisitors() {
    synchronized(myProject) {
      int num = myProject.getUserData(HIGHLIGHT_VISITOR_THREADS_IN_USE).intValue();
      myProject.putUserData(HIGHLIGHT_VISITOR_THREADS_IN_USE, num == 1 ? null : num-1);
    }
  }

  public void doCollectInformation(ProgressIndicator progress) {
    PsiElement[] psiRoots = myFile.getPsiRoots();

    if (myUpdateAll) {
      DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
      RefCountHolder refCountHolder = daemonCodeAnalyzer.getFileStatusMap().getRefCountHolder(myDocument, myFile);
      setRefCountHolders(refCountHolder);

      PsiElement dirtyScope = daemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(myDocument, FileStatusMap.NORMAL_HIGHLIGHTERS);
      if (dirtyScope != null) {
        if (dirtyScope instanceof PsiFile) {
          refCountHolder.clear();
        }
        else {
          refCountHolder.removeInvalidRefs();
        }
      }
    }
    else {
      setRefCountHolders(null);
    }
    Collection<HighlightInfo> result = new THashSet<HighlightInfo>(100);
    try {
      for (final PsiElement psiRoot : psiRoots) {
        if(!HighlightUtil.isRootHighlighted(psiRoot)) continue;
        //long time = System.currentTimeMillis();
        List<PsiElement> elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
        if (elements.isEmpty()) {
          elements = Collections.singletonList(psiRoot);
        }
        //LOG.debug("Elements collected for: " + (System.currentTimeMillis() - time) / 1000.0 + "s");
        //time = System.currentTimeMillis();

        myMarkers = collectLineMarkers(elements);
        //LOG.debug("Line markers collected for: " + (System.currentTimeMillis() - time) / 1000.0 + "s");

        Collection<HighlightInfo> highlights1 = collectHighlights(elements);
        Collection<HighlightInfo> highlights2 = collectTextHighlights();
        addHighlights(result, highlights1);
        addHighlights(result, highlights2);
      }
    }
    finally {
      if (myHighlightVisitors != null) {
        setRefCountHolders(null);
        releaseHighlightVisitors();
      }
    }
    myHighlights = result;
    reportToProblemsToolWindow(result);
  }

  private void reportToProblemsToolWindow(final Collection<HighlightInfo> infos) {
    if (!myFile.getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    ProblemsToolWindow problemsToolWindow = ProblemsToolWindow.getInstance(myProject);
    VirtualFile file = myFile.getVirtualFile();
    String groupName = file.getPresentableUrl();
    problemsToolWindow.clearGroupChildren(file);
    Document document = FileDocumentManager.getInstance().getDocument(file);
    for (HighlightInfo info : infos) {
      HighlightSeverity severity = info.getSeverity();
      if (/*severity != HighlightSeverity.WARNING && */severity != HighlightSeverity.ERROR) {
        continue;
      }
      OpenFileDescriptor navigatable = new OpenFileDescriptor(myProject, file, info.fixStartOffset);
      int line = document.getLineNumber(info.fixStartOffset);
      int column = info.fixStartOffset - document.getLineStartOffset(line);
      String prefix = "("+line + ", " + column + ")";
      problemsToolWindow.addMessage(getKind(info),new String[]{info.description}, groupName, navigatable,  "", prefix, file);
    }
  }
   
  private static int getKind(final HighlightInfo info) {
    HighlightSeverity severity = info.getSeverity();
    if (severity == HighlightSeverity.INFORMATION) return MessageCategory.INFORMATION;
    if (severity == HighlightSeverity.WARNING) return MessageCategory.WARNING;
    if (severity == HighlightSeverity.ERROR) return MessageCategory.ERROR;
    return MessageCategory.STATISTICS;
  }


  private void addHighlights(Collection<HighlightInfo> result, Collection<HighlightInfo> highlights) {
    if (myCompiled) {
      for (final HighlightInfo info : highlights) {
        if (info.getSeverity() == HighlightSeverity.INFORMATION) {
          result.add(info);
        }
      }
    }
    else {
      result.addAll(highlights);
    }
  }

  private void setRefCountHolders(RefCountHolder refCountHolder) {
    for (HighlightVisitor visitor : myHighlightVisitors) {
      visitor.setRefCountHolder(refCountHolder);
    }
  }

  public void doApplyInformationToEditor() {
    UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                  myMarkers, UpdateHighlightersUtil.NORMAL_MARKERS_GROUP);

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                   myHighlights, UpdateHighlightersUtil.NORMAL_HIGHLIGHTERS_GROUP);
  }

  public int getPassId() {
    return myUpdateAll ? Pass.UPDATE_ALL : Pass.UPDATE_VISIBLE;
  }

  public Collection<LineMarkerInfo> queryLineMarkers() {
    try {
      if (myFile.getNode() == null) {
        // binary file? see IDEADEV-2809
        return new ArrayList<LineMarkerInfo>();
      }
      return collectLineMarkers(CodeInsightUtil.getElementsInRange(myFile, myStartOffset, myEndOffset));
    }
    catch (ProcessCanceledException e) {
      return null;
    }
  }

  //for tests only
  @NotNull public Collection<HighlightInfo> getHighlights() {
    return myHighlights;
  }

  private Collection<HighlightInfo> collectHighlights(final List<PsiElement> elements) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Set<PsiElement> skipParentsSet = new THashSet<PsiElement>();
    final Set<HighlightInfo> gotHighlights = new THashSet<HighlightInfo>();
    //long totalTime = 0;
    //if (LOG.isDebugEnabled()) {
    //  totalTime = System.currentTimeMillis();
    //}

    final List<HighlightVisitor> visitors = new ArrayList<HighlightVisitor>();
    for (HighlightVisitor visitor : myHighlightVisitors) {
      if (visitor.suitableForFile(myFile)) visitors.add(visitor);
    }

    final HighlightInfoFilter[] filters = ApplicationManager.getApplication().getComponents(HighlightInfoFilter.class);

    final HighlightInfoHolder holder = new HighlightInfoHolder(myFile, filters);
    PsiManager.getInstance(myProject).performActionWithFormatterDisabled(new Runnable() {
      public void run() {
        for (PsiElement element : elements) {
          ProgressManager.getInstance().checkCanceled();

          if (element != myFile && skipParentsSet.contains(element)) {
            skipParentsSet.add(element.getParent());
            continue;
          }

          try {
            holder.setWritable(true);
            holder.clear();
            for (HighlightVisitor visitor : visitors) {
              visitor.visit(element, holder);
            }
          }
          finally {
            holder.setWritable(false);
          }

          //noinspection ForLoopReplaceableByForEach
          for (int i=0; i<holder.size(); i++) {
            HighlightInfo info = holder.get(i);
            // have to filter out already obtained highlights
            if (!gotHighlights.add(info)) continue;
            if (info.getSeverity() == HighlightSeverity.ERROR) {
              skipParentsSet.add(element.getParent());
            }
          }
        }
      }
    });

    //if (LOG.isDebugEnabled()) {
      //if(maxVisitElement != null){
      //  LOG.debug("maxVisitTime = " + maxVisitTime);
      //  LOG.debug("maxVisitElement = " + maxVisitElement+ " ");
      //}
      //LOG.debug("totalTime = " + (System.currentTimeMillis() - totalTime) / (double)1000 + "s for " + elements.length + " elements");
    //}

    return gotHighlights;
  }

  private Collection<HighlightInfo> collectTextHighlights() {
    PsiManager psiManager = myFile.getManager();
    PsiSearchHelper helper = psiManager.getSearchHelper();
    TodoItem[] todoItems = helper.findTodoItems(myFile, myStartOffset, myEndOffset);
    List<HighlightInfo> list = new ArrayList<HighlightInfo>();
    for (TodoItem todoItem : todoItems) {
      TextRange range = todoItem.getTextRange();
      String description = myDocument.getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.TODO, range, description,
                                                             todoItem.getPattern().getAttributes().getTextAttributes());
      list.add(info);
    }
    return list;
  }


  private Collection<LineMarkerInfo> collectLineMarkers(List<PsiElement> elements) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>();
    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      LineMarkerInfo info = getLineMarkerInfo(element);
      if (info != null) {
        array.add(info);
      }
    }
    return array;
  }

  private LineMarkerInfo getLineMarkerInfo(PsiElement element) {
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element.getParent();
      int offset = element.getTextRange().getStartOffset();
      EjbMethodRole role = EjbRolesUtil.getEjbRolesUtil().getEjbRole(method);

      if (role instanceof EjbImplMethodRole && ((EjbImplMethodRole) role).findAllDeclarations().length != 0) {
        return new LineMarkerInfo(LineMarkerInfo.OVERRIDING_METHOD, method, offset, IMPLEMENTING_METHOD_ICON);
      }

      PsiMethod[] methods = method.findSuperMethods(false);
      if (methods.length > 0) {
        boolean overrides = false;
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          overrides = true;
        }
        else if (!methods[0].hasModifierProperty(PsiModifier.ABSTRACT)) {
          overrides = true;
        }

        return new LineMarkerInfo(LineMarkerInfo.OVERRIDING_METHOD, method, offset,
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
          LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.METHOD_SEPARATOR, element, element.getTextRange().getStartOffset(), null);
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

}
