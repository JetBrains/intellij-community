package com.intellij.extapi.psi;

import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 25, 2005
 * Time: 9:40:47 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class PsiFileBase extends PsiFileImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.extapi.psi.PsiFileBase");
  private final Language myLanguage;

  protected PsiFileBase(FileViewProvider viewProvider, @NotNull Language language) {
    super(language.getParserDefinition().getFileNodeType(), language.getParserDefinition().getFileNodeType(), viewProvider);
    myLanguage = language;
  }

  @NotNull
  public final Language getLanguage() {
    return myLanguage;
  }

  public final Lexer createLexer() {
    return myLanguage.getParserDefinition().createLexer(getProject());
  }

  protected final FileElement createFileElement(final CharSequence docText) {
    final ParserDefinition parserDefinition = myLanguage.getParserDefinition();
    if(parserDefinition != null && parserDefinition.createParser(getProject()) != PsiUtil.NULL_PARSER) {
      return _createFileElement(docText, myLanguage, getProject());
    }
    return super.createFileElement(docText);
  }

  private static FileElement _createFileElement(final CharSequence docText, final Language language, Project project) {
    final ParserDefinition parserDefinition = language.getParserDefinition();
    final PsiParser parser = parserDefinition.createParser(project);
    final IElementType root = parserDefinition.getFileNodeType();
    final PsiBuilderImpl builder = new PsiBuilderImpl(language, project, null, docText);
    final FileElement fileRoot = (FileElement) parser.parse(root, builder);
    LOG.assertTrue(fileRoot.getElementType() == root, "Parsing file text returns rootElement with type different from declared in parser definition");
    return fileRoot;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }
}
