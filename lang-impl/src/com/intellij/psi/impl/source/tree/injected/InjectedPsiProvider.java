package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
*/
class InjectedPsiProvider implements ParameterizedCachedValueProvider<Places, PsiElement> {
  private static class PlacesImpl extends SmartList<InjectedLanguageUtil.Place> implements Places {}

  public CachedValueProvider.Result<Places> compute(PsiElement element) {
    PsiFile hostPsiFile = element.getContainingFile();
    if (hostPsiFile == null) return null;
    FileViewProvider viewProvider = hostPsiFile.getViewProvider();
    final DocumentEx hostDocument = (DocumentEx)viewProvider.getDocument();
    if (hostDocument == null) return null;

    PsiManager psiManager = viewProvider.getManager();
    final Project project = psiManager.getProject();
    InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);
    if (injectedManager == null) return null; //for tests
    final Places result = doCompute(element, injectedManager, project, hostPsiFile);

    return new CachedValueProvider.Result<Places>(result, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
  }

  @Nullable
  static Places doCompute(final PsiElement element, InjectedLanguageManagerImpl injectedManager, Project project, PsiFile hostPsiFile) {
    MyInjProcessor processor = new MyInjProcessor(injectedManager, project, hostPsiFile);
    injectedManager.processInPlaceInjectorsFor(element, processor);
    return processor.hostRegistrar == null ? null : processor.hostRegistrar.result;
  }

  private static class MyInjProcessor implements InjectedLanguageManagerImpl.InjProcessor {
    private MyMultiHostRegistrar hostRegistrar;
    private final InjectedLanguageManagerImpl myInjectedManager;
    private final Project myProject;
    private final PsiFile myHostPsiFile;

    public MyInjProcessor(InjectedLanguageManagerImpl injectedManager, Project project, PsiFile hostPsiFile) {
      myInjectedManager = injectedManager;
      myProject = project;
      myHostPsiFile = hostPsiFile;
    }

    public boolean process(PsiElement element, MultiHostInjector injector) {
      if (hostRegistrar == null) {
        hostRegistrar = new MyMultiHostRegistrar(myProject, myInjectedManager, myHostPsiFile, element);
      }
      injector.getLanguagesToInject(hostRegistrar, element);
      return hostRegistrar.result == null;
    }
  }

  private static class MyMultiHostRegistrar implements MultiHostRegistrar {
    private Places result;
    private Language myLanguage;
    private List<PsiLanguageInjectionHost> injectionHosts;
    private List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers;
    private List<PsiLanguageInjectionHost.Shred> shreds;
    private StringBuilder outChars;
    boolean isOneLineEditor;
    boolean cleared;
    private final Project myProject;
    private final PsiManager myPsiManager;
    private DocumentEx myHostDocument;
    private VirtualFile myHostVirtualFile;
    private final InjectedLanguageManagerImpl myInjectedManager;
    private final PsiElement myContextElement;
    private final PsiFile myHostPsiFile;

    public MyMultiHostRegistrar(@NotNull Project project, @NotNull InjectedLanguageManagerImpl injectedManager, @NotNull PsiFile hostPsiFile, @NotNull PsiElement contextElement) {
      myProject = project;
      myInjectedManager = injectedManager;
      myContextElement = contextElement;
      myHostPsiFile = PsiUtilBase.getTemplateLanguageFile(hostPsiFile);
      myPsiManager = myHostPsiFile.getManager();
      cleared = true;
    }

    @NotNull
    public MultiHostRegistrar startInjecting(@NotNull Language language) {
      injectionHosts = new SmartList<PsiLanguageInjectionHost>();
      escapers = new SmartList<LiteralTextEscaper<? extends PsiLanguageInjectionHost>>();
      shreds = new SmartList<PsiLanguageInjectionHost.Shred>();
      outChars = new StringBuilder();

      if (!cleared) {
        clear();
        throw new IllegalStateException("Seems you haven't called doneInjecting()");
      }

      if (LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) {
        throw new UnsupportedOperationException("Cannot inject language '" + language + "' since its getParserDefinition() returns null");
      }
      myLanguage = language;

      FileViewProvider viewProvider = myHostPsiFile.getViewProvider();
      myHostVirtualFile = viewProvider.getVirtualFile();
      myHostDocument = (DocumentEx)viewProvider.getDocument();
      assert myHostDocument != null : myHostPsiFile + "; " + viewProvider;
      return this;
    }

    private void clear() {
      injectionHosts.clear();
      escapers.clear();
      shreds.clear();
      outChars.setLength(0);
      isOneLineEditor = false;
      myLanguage = null;

      cleared = true;
    }

    @NotNull
    public MultiHostRegistrar addPlace(@NonNls @Nullable String prefix,
                                       @NonNls @Nullable String suffix,
                                       @NotNull PsiLanguageInjectionHost host,
                                       @NotNull TextRange rangeInsideHost) {
      ProperTextRange.assertProperRange(rangeInsideHost);

      PsiFile containingFile = PsiUtilBase.getTemplateLanguageFile(host);
      assert containingFile == myHostPsiFile : "Trying to inject into foreign file: "+containingFile+" while processing injections for "+myHostPsiFile;
      TextRange hostTextRange = host.getTextRange();
      if (!hostTextRange.contains(rangeInsideHost.shiftRight(hostTextRange.getStartOffset()))) {
        clear();
        throw new IllegalArgumentException("rangeInsideHost must lie within host text range. rangeInsideHost:"+rangeInsideHost+"; host textRange:"+
                                           hostTextRange);
      }
      if (myLanguage == null) {
        clear();
        throw new IllegalStateException("Seems you haven't called startInjecting()");
      }

      if (prefix == null) prefix = "";
      if (suffix == null) suffix = "";
      cleared = false;
      injectionHosts.add(host);
      int startOffset = outChars.length();
      outChars.append(prefix);
      LiteralTextEscaper<? extends PsiLanguageInjectionHost> textEscaper = host.createLiteralTextEscaper();
      escapers.add(textEscaper);
      isOneLineEditor |= textEscaper.isOneLine();
      TextRange relevantRange = textEscaper.getRelevantTextRange().intersection(rangeInsideHost);
      if (relevantRange == null) {
        relevantRange = TextRange.from(textEscaper.getRelevantTextRange().getStartOffset(), 0);
      }
      else {
        boolean result = textEscaper.decode(relevantRange, outChars);
        if (!result) {
          // if there are invalid chars, adjust the range
          int offsetInHost = textEscaper.getOffsetInHost(outChars.length() - startOffset, rangeInsideHost);
          relevantRange = relevantRange.intersection(new ProperTextRange(0, offsetInHost));
        }
      }
      outChars.append(suffix);
      int endOffset = outChars.length();
      TextRange relevantRangeInHost = relevantRange.shiftRight(hostTextRange.getStartOffset());
      RangeMarker relevantMarker = myHostDocument.createRangeMarker(relevantRangeInHost);
      relevantMarker.setGreedyToLeft(true);
      relevantMarker.setGreedyToRight(true);
      shreds.add(new PsiLanguageInjectionHost.Shred(host, relevantMarker, prefix, suffix, new ProperTextRange(startOffset, endOffset)));
      return this;
    }

    private static final Key<ASTNode> TREE_HARD_REF = Key.create("TREE_HARD_REF");
    public void doneInjecting() {
      try {
        if (shreds.isEmpty()) {
          throw new IllegalStateException("Seems you haven't called addPlace()");
        }
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        assert ArrayUtil.indexOf(documentManager.getUncommittedDocuments(), myHostDocument) == -1;
        assert myHostPsiFile.getText().equals(myHostDocument.getText());

        DocumentWindowImpl documentWindow = new DocumentWindowImpl(myHostDocument, isOneLineEditor, shreds);
        VirtualFileWindowImpl virtualFile = (VirtualFileWindowImpl)myInjectedManager.createVirtualFile(myLanguage, myHostVirtualFile, documentWindow, outChars);
        myLanguage = LanguageSubstitutors.INSTANCE.substituteLanguage(myLanguage, virtualFile, myProject);
        virtualFile.setLanguage(myLanguage);

        DocumentImpl decodedDocument = new DocumentImpl(outChars);
        FileDocumentManagerImpl.registerDocument(decodedDocument, virtualFile);

        SingleRootFileViewProvider viewProvider = new InjectedFileViewProvider(myPsiManager, virtualFile, shreds);
        ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(myLanguage);
        assert parserDefinition != null;
        PsiFile psiFile = parserDefinition.createFile(viewProvider);
        assert InjectedLanguageUtil.isInjectedFragment(psiFile) : psiFile.getViewProvider();

        SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = createHostSmartPointer(injectionHosts.get(0));
        psiFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, pointer);

        final ASTNode parsedNode = psiFile.getNode();
        assert parsedNode instanceof FileElement : parsedNode;

        String documentText = documentWindow.getText();
        assert outChars.toString().equals(parsedNode.getText()) : "Before patch: doc:\n" + documentText + "\n---PSI:\n" + parsedNode.getText() + "\n---chars:\n"+outChars;
        try {
          patchLeafs(parsedNode, escapers, shreds);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          throw new RuntimeException("Patch error, lang="+myLanguage+";\n "+myHostVirtualFile+"; places:"+injectionHosts+";\n ranges:"+shreds, e);
        }
        assert parsedNode.getText().equals(documentText) : "After patch: doc:\n" + documentText + "\n---PSI:\n" + parsedNode.getText() + "\n---chars:\n"+outChars;

        ((FileElement)parsedNode).setManager((PsiManagerEx)myPsiManager);

        virtualFile.setContent(null, documentWindow.getText(), false);
        FileDocumentManagerImpl.registerDocument(documentWindow, virtualFile);
        synchronized (PsiLock.LOCK) {
          psiFile = registerDocument(documentWindow, psiFile, virtualFile, shreds, myHostPsiFile, documentManager);
          InjectedFileViewProvider myFileViewProvider = (InjectedFileViewProvider)psiFile.getViewProvider();
          myFileViewProvider.setVirtualFile(virtualFile);
          myFileViewProvider.forceCachedPsi(psiFile);

          // need to keep tree reacheable to avoid being garbage-collected (via WeakReference in PsiFileImpl)
          // and thus being reparsed from wrong (escaped) document content
          ASTNode node = psiFile.getNode();
          assert !(node.getFirstChildNode() instanceof ChameleonElement);
          psiFile.putUserData(TREE_HARD_REF, node);
        }

        try {
          List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = obtainHighlightTokensFromLexer(myLanguage, outChars, escapers, shreds, virtualFile, myProject);
          psiFile.putUserData(InjectedLanguageUtil.HIGHLIGHT_TOKENS, tokens);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          throw new RuntimeException("Patch error, lang="+myLanguage+";\n "+myHostVirtualFile+"; places:"+injectionHosts+";\n ranges:"+shreds, e);
        }

        PsiDocumentManagerImpl.checkConsistency(psiFile, documentWindow);

        InjectedLanguageUtil.Place place = new InjectedLanguageUtil.Place(psiFile, new ArrayList<PsiLanguageInjectionHost.Shred>(shreds));
        if (result == null) {
          result = new PlacesImpl();
        }
        result.add(place);

        boolean isAncestor = false;
        for (PsiLanguageInjectionHost.Shred shred : shreds) {
          PsiLanguageInjectionHost host = shred.host;
          isAncestor |= PsiTreeUtil.isAncestor(myContextElement, host, false);
        }
        assert isAncestor : myContextElement + " must be the parent of at least one of injection hosts: " + shreds;
      }
      finally {
        clear();
      }
    }
  }

  private static void patchLeafs(final ASTNode parsedNode,
                                 final List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers,
                                 final List<PsiLanguageInjectionHost.Shred> shreds) {
    final Map<LeafElement, String> newTexts = new THashMap<LeafElement, String>();
    final StringBuilder catLeafs = new StringBuilder();
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementVisitor(){
      int currentHostNum = -1;
      LeafElement prevElement;
      String prevElementTail;
      int prevHostsCombinedLength;
      TextRange shredHostRange;
      TextRange rangeInsideHost;
      String hostText;
      PsiLanguageInjectionHost.Shred shred;
      int prefixLength;
      {
        incHostNum(0);
      }

      protected boolean visitNode(TreeElement element) {
        return true;
      }

      @Override public void visitLeaf(LeafElement leaf) {
        String leafText = leaf.getText();
        catLeafs.append(leafText);
        TextRange range = leaf.getTextRange();
        int startOffsetInHost;
        while (true) {
          if (prefixLength > range.getStartOffset() && prefixLength < range.getEndOffset()) {
            //LOG.error("Prefix must not contain text that will be glued with the element body after parsing. " +
            //          "However, parsed element of "+leaf.getClass()+" contains "+(prefixLength-range.getStartOffset()) + " characters from the prefix. " +
            //          "Parsed text is '"+leaf.getText()+"'");
          }
          if (range.getStartOffset() < shredHostRange.getEndOffset() && shredHostRange.getEndOffset() < range.getEndOffset()) {
            //LOG.error("Suffix must not contain text that will be glued with the element body after parsing. " +
            //          "However, parsed element of "+leaf.getClass()+" contains "+(range.getEndOffset()-shredHostRange.getEndOffset()) + " characters from the suffix. " +
            //          "Parsed text is '"+leaf.getText()+"'");
          }

          int start = range.getStartOffset() - prevHostsCombinedLength;
          if (start < prefixLength) return;
          int end = range.getEndOffset();
          if (end > shred.range.getEndOffset() - shred.suffix.length() && end <= shred.range.getEndOffset()) return;
          startOffsetInHost = escapers.get(currentHostNum).getOffsetInHost(start - prefixLength, rangeInsideHost);

          if (startOffsetInHost != -1 && startOffsetInHost != rangeInsideHost.getEndOffset()) {
            break;
          }
          // no way next leaf might stand more than one shred apart
          incHostNum(range.getStartOffset());
        }
        String leafEncodedText = "";
        while (true) {
          if (range.getEndOffset() <= shred.range.getEndOffset()) {
            int end = range.getEndOffset() - prevHostsCombinedLength;
            if (end < prefixLength) {
              leafEncodedText += shred.prefix.substring(0, end);
            }
            else {
              int endOffsetInHost = escapers.get(currentHostNum).getOffsetInHost(end - prefixLength, rangeInsideHost);
              assert endOffsetInHost != -1;
              leafEncodedText += hostText.substring(startOffsetInHost, endOffsetInHost);
            }
            break;
          }
          String rest = hostText.substring(startOffsetInHost, rangeInsideHost.getEndOffset());
          leafEncodedText += rest;
          incHostNum(shred.range.getEndOffset());
          leafEncodedText += shred.prefix;
          startOffsetInHost = shred.getRangeInsideHost().getStartOffset();
        }

        if (leaf.getElementType() == TokenType.WHITE_SPACE && prevElementTail != null) {
          // optimization: put all garbage into whitespace
          leafEncodedText = prevElementTail + leafEncodedText;
          newTexts.remove(prevElement);
          storeUnescapedTextFor(prevElement, null);
        }
        if (!Comparing.strEqual(leafText, leafEncodedText)) {
          newTexts.put(leaf, leafEncodedText);
          storeUnescapedTextFor(leaf, leafText);
        }
        if (leafEncodedText.startsWith(leafText) && leafEncodedText.length() != leafText.length()) {
          prevElementTail = leafEncodedText.substring(leafText.length());
        }
        else {
          prevElementTail = null;
        }
        prevElement = leaf;
      }

      private void incHostNum(int startOffset) {
        currentHostNum++;
        prevHostsCombinedLength = startOffset;
        shred = shreds.get(currentHostNum);
        shredHostRange = new ProperTextRange(TextRange.from(shred.prefix.length(), shred.getRangeInsideHost().getLength()));
        rangeInsideHost = shred.getRangeInsideHost();
        hostText = shred.host.getText();
        prefixLength = shredHostRange.getStartOffset();
      }
    });

    String nodeText = parsedNode.getText();
    assert nodeText.equals(catLeafs.toString()) : "Malformed PSI structure: leaf texts do not add up to the whole file text." +
                                                  "\nFile text (from tree)  :'"+nodeText+"'" +
                                                  "\nFile text (from PSI)   :'"+parsedNode.getPsi().getText()+"'" +
                                                  "\nLeaf texts concatenated:'"+catLeafs+"';" +
                                                  "\nFile root: "+parsedNode+
                                                  "\nLanguage: "+parsedNode.getPsi().getLanguage()+
                                                  "\nHost file: "+shreds.get(0).host.getContainingFile().getVirtualFile()
        ;
    for (LeafElement leaf : newTexts.keySet()) {
      String newText = newTexts.get(leaf);
      leaf.setText(newText);
    }
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementVisitor(){
      protected boolean visitNode(TreeElement element) {
        element.clearCaches();
        return true;
      }
    });
  }

  private static void storeUnescapedTextFor(final LeafElement leaf, final String leafText) {
    PsiElement psi = leaf.getPsi();
    if (psi != null) {
      psi.putUserData(InjectedLanguageManagerImpl.UNESCAPED_TEXT, leafText);
    }
  }

  private static <T extends PsiLanguageInjectionHost> SmartPsiElementPointer<T> createHostSmartPointer(final T host) {
    return host.isPhysical()
           ? SmartPointerManager.getInstance(host.getProject()).createSmartPsiElementPointer(host)
           : new SmartPsiElementPointer<T>() {
             public T getElement() {
               return host;
             }

             public PsiFile getContainingFile() {
               return host.getContainingFile();
             }
           };
  }
  private static PsiFile registerDocument(final DocumentWindowImpl documentWindow,
                                          final PsiFile injectedPsi,
                                          VirtualFileWindowImpl virtualFile,
                                          List<PsiLanguageInjectionHost.Shred> shreds,
                                          final PsiFile hostPsiFile,
                                          PsiDocumentManager documentManager) {
    DocumentEx hostDocument = documentWindow.getDelegate();
    List<DocumentWindow> injected = InjectedLanguageUtil.getCachedInjectedDocuments(hostPsiFile);

    for (int i = injected.size()-1; i>=0; i--) {
      DocumentWindowImpl oldDocument = (DocumentWindowImpl)injected.get(i);
      PsiFileImpl oldFile = (PsiFileImpl)documentManager.getCachedPsiFile(oldDocument);
      FileViewProvider oldViewProvider;

      if (oldFile == null ||
          !oldFile.isValid() ||
          !((oldViewProvider = oldFile.getViewProvider()) instanceof InjectedFileViewProvider) ||
          !((InjectedFileViewProvider)oldViewProvider).isValid()
          ) {
        injected.remove(i);
        Disposer.dispose(oldDocument);
        continue;
      }

      ASTNode injectedNode = injectedPsi.getNode();
      ASTNode oldFileNode = oldFile.getNode();
      assert injectedNode != null;
      assert oldFileNode != null;
      if (oldDocument.areRangesEqual(documentWindow)) {
        if (oldFile.getFileType() != injectedPsi.getFileType() || oldFile.getLanguage() != injectedPsi.getLanguage()) {
          injected.remove(i);
          Disposer.dispose(oldDocument);
          continue;
        }
        oldFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, injectedPsi.getUserData(FileContextUtil.INJECTED_IN_ELEMENT));
        if (!isPSItheSame(injectedNode, oldFileNode)) {
          // replace psi
          FileElement newFileElement = (FileElement)injectedNode;
          FileElement oldFileElement = oldFile.getTreeElement();

          if (oldFileElement.getFirstChildNode() != null) {
            TreeUtil.removeRange(oldFileElement.getFirstChildNode(), null);
          }
          final ASTNode firstChildNode = newFileElement.getFirstChildNode();
          if (firstChildNode != null) {
            TreeUtil.addChildren(oldFileElement, (TreeElement)firstChildNode);
          }
          oldFileElement.setCharTable(newFileElement.getCharTable());
          FileDocumentManagerImpl.registerDocument(documentWindow, oldFile.getVirtualFile());

          InjectedFileViewProvider viewProvider = (InjectedFileViewProvider)oldViewProvider;
          viewProvider.replace(virtualFile, shreds);

          oldFile.subtreeChanged();
        }
        return oldFile;
      }
    }
    injected.add(documentWindow);
    List<RangeMarker> injectedRegions = InjectedLanguageUtil.getCachedInjectedRegions(hostDocument);
    RangeMarker newMarker = documentWindow.getHostRanges()[0];
    TextRange newRange = InjectedLanguageUtil.toTextRange(newMarker);
    for (int i = 0; i < injectedRegions.size(); i++) {
      RangeMarker stored = injectedRegions.get(i);
      TextRange storedRange = InjectedLanguageUtil.toTextRange(stored);
      if (storedRange.intersects(newRange)) {
        injectedRegions.set(i, newMarker);
        break;
      }
      if (storedRange.getStartOffset() > newRange.getEndOffset()) {
        injectedRegions.add(i, newMarker);
        break;
      }
    }
    if (injectedRegions.isEmpty() || newRange.getStartOffset() > injectedRegions.get(injectedRegions.size()-1).getEndOffset()) {
      injectedRegions.add(newMarker);
    }
    return injectedPsi;
  }

  private static boolean isPSItheSame(ASTNode injectedNode, ASTNode oldFileNode) {
    //boolean textSame = injectedNode.getText().equals(oldFileNode.getText());

    boolean psiSame = comparePSI(injectedNode, oldFileNode);
    //if (psiSame != textSame) {
    //  throw new RuntimeException(textSame + ";" + psiSame);
    //}
    return psiSame;
  }

  private static boolean comparePSI(ASTNode injectedNode, ASTNode oldFileNode) {
    if (injectedNode instanceof LeafElement) {
      return oldFileNode instanceof LeafElement &&
             injectedNode.getElementType().equals(oldFileNode.getElementType()) &&
             injectedNode.getText().equals(oldFileNode.getText());
    }
    if (!(injectedNode instanceof CompositeElement) || !(oldFileNode instanceof CompositeElement)) return false;
    CompositeElement element1 = (CompositeElement)injectedNode;
    CompositeElement element2 = (CompositeElement)oldFileNode;
    ChameleonTransforming.transformChildren(element1);
    ChameleonTransforming.transformChildren(element2);
    TreeElement child1 = element1.getFirstChildNode();
    TreeElement child2 = element2.getFirstChildNode();
    while (child1  != null && child2 != null) {
      if (!comparePSI(child1, child2)) return false;
      child1 = child1.getTreeNext();
      child2 = child2.getTreeNext();
    }

    return child1 == null && child2 == null;
  }
  // returns lexer elemet types with corresponsing ranges in encoded (injection host based) PSI
  private static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> obtainHighlightTokensFromLexer(Language language,
                                                                                                                 StringBuilder outChars,
                                                                                                                 List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers,
                                                                                                                 List<PsiLanguageInjectionHost.Shred> shreds,
                                                                                                                 VirtualFileWindow virtualFile,
                                                                                                                 Project project) {
    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = new ArrayList<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>(10);
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, (VirtualFile)virtualFile);
    Lexer lexer = syntaxHighlighter.getHighlightingLexer();
    lexer.start(outChars, 0, outChars.length(), 0);
    int hostNum = -1;
    int prevHostEndOffset = 0;
    PsiLanguageInjectionHost host = null;
    LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = null;
    int prefixLength = 0;
    int suffixLength = 0;
    TextRange rangeInsideHost = null;
    int shredEndOffset = -1;
    for (IElementType tokenType = lexer.getTokenType(); tokenType != null; lexer.advance(), tokenType = lexer.getTokenType()) {
      TextRange range = new ProperTextRange(lexer.getTokenStart(), lexer.getTokenEnd());
      while (range != null && !range.isEmpty()) {
        if (range.getStartOffset() >= shredEndOffset) {
          hostNum++;
          shredEndOffset = shreds.get(hostNum).range.getEndOffset();
          prevHostEndOffset = range.getStartOffset();
          host = shreds.get(hostNum).host;
          escaper = escapers.get(hostNum);
          rangeInsideHost = shreds.get(hostNum).getRangeInsideHost();
          prefixLength = shreds.get(hostNum).prefix.length();
          suffixLength = shreds.get(hostNum).suffix.length();
        }
        //in prefix/suffix or spills over to next fragment
        if (range.getStartOffset() < prevHostEndOffset + prefixLength) {
          range = new TextRange(prevHostEndOffset + prefixLength, range.getEndOffset());
        }
        TextRange spilled = null;
        if (range.getEndOffset() >= shredEndOffset - suffixLength) {
          spilled = new TextRange(shredEndOffset, range.getEndOffset());
          range = new TextRange(range.getStartOffset(), shredEndOffset);
        }
        if (!range.isEmpty()) {
          int start = escaper.getOffsetInHost(range.getStartOffset() - prevHostEndOffset - prefixLength, rangeInsideHost);
          int end = escaper.getOffsetInHost(range.getEndOffset() - prevHostEndOffset - prefixLength, rangeInsideHost);
          if (end == -1) {
            end = rangeInsideHost.getEndOffset();
            tokens.add(Trinity.<IElementType, PsiLanguageInjectionHost, TextRange>create(tokenType, host, new ProperTextRange(start, end)));
            prevHostEndOffset = shredEndOffset;
          }
          else {
            TextRange rangeInHost = new ProperTextRange(start, end);
            tokens.add(Trinity.create(tokenType, host, rangeInHost));
          }
        }
        range = spilled;
      }
    }
    return tokens;
  }
}
