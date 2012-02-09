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

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
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
public class MultiHostRegistrarImpl implements MultiHostRegistrar, ModificationTracker {
  private List<Pair<Place, PsiFile>> result;
  private Language myLanguage;
  private List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers;
  private List<PsiLanguageInjectionHost.Shred> shreds;
  private StringBuilder outChars;
  private boolean isOneLineEditor;
  private boolean cleared;
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final DocumentEx myHostDocument;
  private final VirtualFile myHostVirtualFile;
  private final PsiElement myContextElement;
  private final PsiFile myHostPsiFile;

  MultiHostRegistrarImpl(@NotNull Project project,
                         @NotNull PsiFile hostPsiFile,
                         @NotNull PsiElement contextElement) {
    myProject = project;
    myContextElement = contextElement;
    myHostPsiFile = PsiUtilCore.getTemplateLanguageFile(hostPsiFile);
    myPsiManager = myHostPsiFile.getManager();
    cleared = true;
    FileViewProvider viewProvider = myHostPsiFile.getViewProvider();
    myHostVirtualFile = viewProvider.getVirtualFile();
    myHostDocument = (DocumentEx)viewProvider.getDocument();
  }

  public List<Pair<Place, PsiFile>> getResult() {
    return result;
  }

  @NotNull
  public PsiElement getContextElement() {
    return myContextElement;
  }

  @Override
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

  @Override
  @NotNull
  public MultiHostRegistrar addPlace(@NonNls @Nullable String prefix,
                                     @NonNls @Nullable String suffix,
                                     @NotNull PsiLanguageInjectionHost host,
                                     @NotNull TextRange rangeInsideHost) {
    ProperTextRange.assertProperRange(rangeInsideHost);

    PsiFile containingFile = PsiUtilCore.getTemplateLanguageFile(host);
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

  @Override
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

      InjectedFileViewProvider viewProvider = new InjectedFileViewProvider(myPsiManager, virtualFile, documentWindow, myLanguage);
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(myLanguage);
      assert parserDefinition != null : "Parser definition for language "+myLanguage+" is null";
      PsiFile psiFile = parserDefinition.createFile(viewProvider);

      SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = createHostSmartPointer(shreds.get(0).host, myHostPsiFile);

      synchronized (PsiLock.LOCK) {
        final ASTNode parsedNode = keepTreeFromChameleoningBack(psiFile);

        assert parsedNode instanceof FileElement : "Parsed to "+parsedNode+" instead of FileElement";

        String documentText = documentWindow.getText();
        assert outChars.toString().equals(parsedNode.getText()) : exceptionContext("Before patch: doc:\n'" + documentText + "'\n---PSI:\n'" + parsedNode.getText() + "'\n---chars:\n'"+outChars+"'");

        viewProvider.setPatchingLeaves(true);
        try {
          patchLeafs(parsedNode, escapers, place);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          throw new RuntimeException(exceptionContext("Patch error"), e);
        }
        finally {
          viewProvider.setPatchingLeaves(false);
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
          InjectedLanguageUtil.clearCaches(psiFile, documentWindow);
          psiFile = newFile;
          viewProvider = (InjectedFileViewProvider)psiFile.getViewProvider();
          documentWindow = (DocumentWindowImpl)viewProvider.getDocument();
          virtualFile = (VirtualFileWindowImpl)viewProvider.getVirtualFile();
          boolean shredsRewritten = cacheEverything(place, documentWindow, viewProvider, psiFile, pointer);
          if (!shredsRewritten) {
            place.dispose();
            place = documentWindow.getShreds();
          }
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

  // returns true if shreds were set, false if old ones were reused
  private static boolean cacheEverything(@NotNull Place place,
                                         @NotNull DocumentWindowImpl documentWindow,
                                         @NotNull InjectedFileViewProvider viewProvider,
                                         @NotNull PsiFile psiFile,
                                         @NotNull SmartPsiElementPointer<PsiLanguageInjectionHost> pointer) {
    FileDocumentManagerImpl.registerDocument(documentWindow, viewProvider.getVirtualFile());

    viewProvider.forceCachedPsi(psiFile);

    psiFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, pointer);
    PsiDocumentManagerImpl.cachePsi(documentWindow, psiFile);

    keepTreeFromChameleoningBack(psiFile);

    return viewProvider.setShreds(place, psiFile.getProject());
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

  @NotNull
  private static <T extends PsiLanguageInjectionHost> SmartPsiElementPointer<T> createHostSmartPointer(@NotNull T host, @NotNull PsiFile hostPsiFile) {
    return hostPsiFile.isPhysical()
           ? SmartPointerManager.getInstance(host.getProject()).createSmartPsiElementPointer(host, hostPsiFile)
           : new IdentitySmartPointer<T>(host, hostPsiFile);
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

    TreeUtil.clearCaches((TreeElement)parsedNode);
  }

  // under com.intellij.psi.PsiLock.LOCK
  private static PsiFile registerDocument(final DocumentWindowImpl documentWindow,
                                          final PsiFile injectedPsi,
                                          final Place shreds,
                                          final PsiFile hostPsiFile,
                                          final PsiDocumentManager documentManager) {
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
          @Override
          public void run() {
            //todo
            final DiffLog diffLog = BlockSupportImpl.mergeTrees(oldFile, oldFileNode, injectedNode, new DaemonProgressIndicator());
            CodeStyleManager.getInstance(hostPsiFile.getProject()).performActionWithFormatterDisabled(new Runnable() {
              @Override
              public void run() {
                synchronized (PsiLock.LOCK) {
                  DocumentCommitThread.doActualPsiChange(oldFile, diffLog);
                }
              }
            });
          }
        });
        assert shreds.isValid();

        return oldFile;
      }
    }
    injected.add(documentWindow);

    return injectedPsi;
  }

  // returns lexer element types with corresponding ranges in encoded (injection host based) PSI
  private static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>
          obtainHighlightTokensFromLexer(Language language,
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
          PsiLanguageInjectionHost.Shred shred = shreds.get(hostNum);
          shredEndOffset = shred.range.getEndOffset();
          prevHostEndOffset = range.getStartOffset();
          host = shred.host;
          escaper = escapers.get(hostNum);
          rangeInsideHost = shred.getRangeInsideHost();
          prefixLength = shred.prefix.length();
          suffixLength = shred.suffix.length();
        }
        //in prefix/suffix or spills over to next fragment
        if (range.getStartOffset() < prevHostEndOffset + prefixLength) {
          range = new TextRange(prevHostEndOffset + prefixLength, range.getEndOffset());
        }
        TextRange spilled = null;
        if (range.getEndOffset() > shredEndOffset - suffixLength) {
          spilled = new TextRange(shredEndOffset, range.getEndOffset());
          range = new TextRange(range.getStartOffset(), shredEndOffset-suffixLength);
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

  // for CachedValue
  @Override
  public long getModificationCount() {
    List<PsiLanguageInjectionHost.Shred> shredList = shreds;
    if (shredList != null) {
      for (PsiLanguageInjectionHost.Shred shred : shredList) {
        if (!shred.isValid()) return -1;
      }
    }
    DocumentEx hostDocument = myHostDocument;
    return hostDocument == null ? -1 : hostDocument.getModificationStamp();
  }

  @Override
  public String toString() {
    return result.toString();
  }
}
