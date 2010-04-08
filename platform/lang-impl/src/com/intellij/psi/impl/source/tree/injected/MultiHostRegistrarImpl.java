/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.injected.editor.VirtualFileWindowImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
*/
public class MultiHostRegistrarImpl implements MultiHostRegistrar {
  List<Pair<Place, PsiFile>> result;
  private Language myLanguage;
  private List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers;
  private List<PsiLanguageInjectionHost.Shred> shreds;
  private StringBuilder outChars;
  private boolean isOneLineEditor;
  private boolean cleared;
  private final Project myProject;
  private final PsiManager myPsiManager;
  private DocumentEx myHostDocument;
  private VirtualFile myHostVirtualFile;
  private final PsiElement myContextElement;
  private final PsiFile myHostPsiFile;

  MultiHostRegistrarImpl(@NotNull Project project, @NotNull PsiFile hostPsiFile, @NotNull PsiElement contextElement) {
    myProject = project;
    myContextElement = contextElement;
    myHostPsiFile = PsiUtilBase.getTemplateLanguageFile(hostPsiFile);
    myPsiManager = myHostPsiFile.getManager();
    cleared = true;
  }

  @NotNull
  public MultiHostRegistrar startInjecting(@NotNull Language language) {
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
    assert containingFile == myHostPsiFile : exceptionContext("Trying to inject into foreign file: "+containingFile);
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
      int before = outChars.length();
      boolean result = textEscaper.decode(relevantRange, outChars);
      int after = outChars.length();
      assert after >= before : "Escaper " + textEscaper + "("+textEscaper.getClass()+") must not mangle char buffer";
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

  public void doneInjecting() {
    try {
      if (shreds.isEmpty()) {
        throw new IllegalStateException("Seems you haven't called addPlace()");
      }
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      assert ArrayUtil.indexOf(documentManager.getUncommittedDocuments(), myHostDocument) == -1 : "document is uncommitted: "+myHostDocument;
      assert myHostPsiFile.getText().equals(myHostDocument.getText()) : "host text mismatch";

      Place place = new Place(shreds);
      DocumentWindowImpl documentWindow = new DocumentWindowImpl(myHostDocument, isOneLineEditor, place);
      VirtualFileWindowImpl virtualFile = new VirtualFileWindowImpl(myHostVirtualFile, documentWindow, myLanguage, outChars);
      myLanguage = LanguageSubstitutors.INSTANCE.substituteLanguage(myLanguage, virtualFile, myProject);
      virtualFile.setLanguage(myLanguage);

      DocumentImpl decodedDocument;
      if (StringUtil.indexOf(outChars, '\r') == -1) {
        decodedDocument = new DocumentImpl(outChars);
      }
      else {
        decodedDocument = new DocumentImpl(true);
        decodedDocument.setAcceptSlashR(true);
        decodedDocument.replaceString(0,0,outChars);
      }
      FileDocumentManagerImpl.registerDocument(decodedDocument, virtualFile);

      InjectedFileViewProvider viewProvider = new InjectedFileViewProvider(myPsiManager, virtualFile, place, documentWindow, myLanguage);
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(myLanguage);
      assert parserDefinition != null : "Parser definition for language "+myLanguage+" is null";
      PsiFile psiFile = parserDefinition.createFile(viewProvider);

      SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = createHostSmartPointer(shreds.get(0).host);

      synchronized (PsiLock.LOCK) {
        final ASTNode parsedNode = keepTreeFromChameleoningBack(psiFile);

        assert parsedNode instanceof FileElement : "Parsed to "+parsedNode+" instead of FileElement";

        String documentText = documentWindow.getText();
        assert outChars.toString().equals(parsedNode.getText()) : exceptionContext("Before patch: doc:\n'" + documentText + "'\n---PSI:\n'" + parsedNode.getText() + "'\n---chars:\n'"+outChars+"'");
        try {
          patchLeafs(parsedNode, escapers, place);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          throw new RuntimeException(exceptionContext("Patch error"), e);
        }
        assert parsedNode.getText().equals(documentText) : exceptionContext("After patch: doc:\n'" + documentText + "'\n---PSI:\n'" + parsedNode.getText() + "'\n---chars:\n'"+outChars+"'");

        virtualFile.setContent(null, documentWindow.getText(), false);
        
        cacheEverything(place, documentWindow, viewProvider, psiFile, pointer);

        PsiFile cachedPsiFile = documentManager.getCachedPsiFile(documentWindow);
        assert cachedPsiFile == psiFile : "Cached psi :"+ cachedPsiFile +" instead of "+psiFile;

        assert place.isValid();
        assert viewProvider.isValid();
        PsiFile newFile = registerDocument(documentWindow, psiFile, place, myHostPsiFile, documentManager);
        boolean mergeHappened = newFile != psiFile;
        if (mergeHappened) {
          InjectedLanguageUtil.clearCaches(psiFile);
          psiFile = newFile;
          viewProvider = (InjectedFileViewProvider)psiFile.getViewProvider();
          documentWindow = (DocumentWindowImpl)viewProvider.getDocument();
          virtualFile = (VirtualFileWindowImpl)viewProvider.getVirtualFile();
          cacheEverything(place, documentWindow, viewProvider, psiFile, pointer);
        }

        assert psiFile.isValid();
        assert place.isValid();
        assert viewProvider.isValid();

        try {
          List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = obtainHighlightTokensFromLexer(myLanguage, outChars, escapers, place, virtualFile, myProject);
          psiFile.putUserData(InjectedLanguageUtil.HIGHLIGHT_TOKENS, tokens);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          throw new RuntimeException(exceptionContext("Obtaining tokens error"), e);
        }

        addToResults(place, psiFile);

        assertEverythingIsAllright(documentManager, documentWindow, psiFile);
      }
    }
    finally {
      clear();
    }
  }

  private static void cacheEverything(Place place,
                                      DocumentWindowImpl documentWindow,
                                      InjectedFileViewProvider viewProvider,
                                      PsiFile psiFile,
                                      SmartPsiElementPointer<PsiLanguageInjectionHost> pointer) {
    FileDocumentManagerImpl.registerDocument(documentWindow, viewProvider.getVirtualFile());

    viewProvider.forceCachedPsi(psiFile);

    psiFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, pointer);
    PsiDocumentManagerImpl.cachePsi(documentWindow, psiFile);

    keepTreeFromChameleoningBack(psiFile);

    viewProvider.setShreds(place);
  }


  @NonNls
  private String exceptionContext(@NonNls String msg) {
    return msg + ".\n" +
           myLanguage+";\n "+
           "Host file: "+myHostPsiFile+" in '" + myHostVirtualFile.getPresentableUrl() + "'\n" +
           "Context element "+myContextElement.getTextRange() + ": '" + myContextElement +"'; "+
           "Ranges: "+shreds;
  }

  private static final Key<ASTNode> TREE_HARD_REF = Key.create("TREE_HARD_REF");
  private static ASTNode keepTreeFromChameleoningBack(PsiFile psiFile) {
    psiFile.getFirstChild();
    // need to keep tree reacheable to avoid being garbage-collected (via WeakReference in PsiFileImpl)
    // and then being reparsed from wrong (escaped) document content
    ASTNode node = psiFile.getNode();
    assert !TreeUtil.isCollapsedChameleon(node) : "Chameleon "+node+" is collapsed";
    psiFile.putUserData(TREE_HARD_REF, node);
    return node;
  }

  private void assertEverythingIsAllright(PsiDocumentManager documentManager, DocumentWindowImpl documentWindow, PsiFile psiFile) {
    boolean isAncestor = false;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      PsiLanguageInjectionHost host = shred.host;
      isAncestor |= PsiTreeUtil.isAncestor(myContextElement, host, false);
    }
    assert isAncestor : exceptionContext(myContextElement + " must be the parent of at least one of injection hosts");

    InjectedFileViewProvider injectedFileViewProvider = (InjectedFileViewProvider)psiFile.getViewProvider();
    assert injectedFileViewProvider.isValid() : "Invalid view provider: "+injectedFileViewProvider;
    assert documentWindow.getText().equals(psiFile.getText()) : "Document window text mismatch";
    assert injectedFileViewProvider.getDocument() == documentWindow : "Provider document mismatch";
    assert documentManager.getCachedDocument(psiFile) == documentWindow : "Cached document mismatch";
    assert psiFile.getVirtualFile() == injectedFileViewProvider.getVirtualFile() : "Virtual file mismatch: "+psiFile.getVirtualFile()+"; "+injectedFileViewProvider.getVirtualFile();
    PsiDocumentManagerImpl.checkConsistency(psiFile, documentWindow);
  }

  void addToResults(Place place, PsiFile psiFile) {
    if (result == null) {
      result = new SmartList<Pair<Place, PsiFile>>();
    }
    result.add(Pair.create(place, psiFile));
  }

  private static <T extends PsiLanguageInjectionHost> SmartPsiElementPointer<T> createHostSmartPointer(final T host) {
    return host.isPhysical()
           ? SmartPointerManager.getInstance(host.getProject()).createSmartPsiElementPointer(host)
           : new IdentitySmartPointer<T>(host);
  }

  private static void patchLeafs(ASTNode parsedNode, List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers, Place shreds) {
    LeafPatcher patcher = new LeafPatcher(shreds, escapers);
    ((TreeElement)parsedNode).acceptTree(patcher);

    String nodeText = parsedNode.getText();
    assert nodeText.equals(patcher.catLeafs.toString()) : "Malformed PSI structure: leaf texts do not add up to the whole file text." +
                                                  "\nFile text (from tree)  :'"+nodeText+"'" +
                                                  "\nFile text (from PSI)   :'"+parsedNode.getPsi().getText()+"'" +
                                                  "\nLeaf texts concatenated:'"+ patcher.catLeafs +"';" +
                                                  "\nFile root: "+parsedNode+
                                                  "\nLanguage: "+parsedNode.getPsi().getLanguage()+
                                                  "\nHost file: "+shreds.get(0).host.getContainingFile().getVirtualFile()
        ;
    for (Map.Entry<LeafElement, String> entry : patcher.newTexts.entrySet()) {
      LeafElement leaf = entry.getKey();
      String newText = entry.getValue();
      leaf.rawReplaceWithText(newText);
    }
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementWalkingVisitor(){
      protected void visitNode(TreeElement element) {
        element.clearCaches();
        super.visitNode(element);
      }
    });
  }

  private static PsiFile registerDocument(final DocumentWindowImpl documentWindow,
                                          final PsiFile injectedPsi,
                                          final Place shreds,
                                          final PsiFile hostPsiFile,
                                          final PsiDocumentManager documentManager) {
    DocumentEx hostDocument = documentWindow.getDelegate();
    List<DocumentWindow> injected = InjectedLanguageUtil.getCachedInjectedDocuments(hostPsiFile);

    for (int i = injected.size()-1; i>=0; i--) {
      DocumentWindowImpl oldDocument = (DocumentWindowImpl)injected.get(i);
      final PsiFileImpl oldFile = (PsiFileImpl)documentManager.getCachedPsiFile(oldDocument);
      FileViewProvider viewProvider;

      if (oldFile == null ||
          !oldFile.isValid() ||
          !((viewProvider = oldFile.getViewProvider()) instanceof InjectedFileViewProvider) ||
          ((InjectedFileViewProvider)viewProvider).isDisposed()
        ) {
        injected.remove(i);
        Disposer.dispose(oldDocument);
        continue;
      }
      InjectedFileViewProvider oldViewProvider = (InjectedFileViewProvider)viewProvider;

      final ASTNode injectedNode = injectedPsi.getNode();
      final ASTNode oldFileNode = oldFile.getNode();
      assert injectedNode != null : "New node is null";
      assert oldFileNode != null : "Old node is null";
      if (oldDocument.areRangesEqual(documentWindow)) {
        if (oldFile.getFileType() != injectedPsi.getFileType() || oldFile.getLanguage() != injectedPsi.getLanguage()) {
          injected.remove(i);
          Disposer.dispose(oldDocument);
          continue;
        }
        oldFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, injectedPsi.getUserData(FileContextUtil.INJECTED_IN_ELEMENT));

        assert shreds.isValid();
        oldViewProvider.performNonPhysically(new Runnable() {
          public void run() {
            BlockSupportImpl.mergeTrees(oldFile, oldFileNode, injectedNode);
          }
        });
        assert shreds.isValid();

        return oldFile;
      }
    }
    injected.add(documentWindow);

    cacheInjectedRegion(documentWindow, hostDocument);
    return injectedPsi;
  }

  private static void cacheInjectedRegion(DocumentWindowImpl documentWindow, DocumentEx hostDocument) {
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
  }

  // returns lexer elemet types with corresponsing ranges in encoded (injection host based) PSI
  private static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> obtainHighlightTokensFromLexer(Language language,
                                                                                                                 StringBuilder outChars,
                                                                                                                 List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers,
                                                                                                                 Place shreds,
                                                                                                                 VirtualFileWindow virtualFile,
                                                                                                                 Project project) {
    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = new ArrayList<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>(10);
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, (VirtualFile)virtualFile);
    Lexer lexer = syntaxHighlighter.getHighlightingLexer();
    lexer.start(outChars);
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
          if (start == -1) start = rangeInsideHost.getStartOffset();
          int end = escaper.getOffsetInHost(range.getEndOffset() - prevHostEndOffset - prefixLength, rangeInsideHost);
          if (end == -1) {
            end = rangeInsideHost.getEndOffset();
            prevHostEndOffset = shredEndOffset;
          }
          TextRange rangeInHost = new ProperTextRange(start, end);
          tokens.add(Trinity.create(tokenType, host, rangeInHost));
        }
        range = spilled;
      }
    }
    return tokens;
  }
}
