package com.intellij.lang;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 16, 2005
 * Time: 9:10:05 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Language {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.Language");

  private static Map<String, Language> ourRegisteredLanguages = new HashMap<String, Language>();
  private String myID;

  protected Language(final String ID) {
    myID = ID;
    if (ourRegisteredLanguages.containsKey(ID)) {
      LOG.error("Language '" + ID + "' is already registered");
      return;
    }
    ourRegisteredLanguages.put(ID, this);
  }

  public static Language findByID(String id) {
    return ourRegisteredLanguages.get(id);
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new PlainSyntaxHighlighter();
  }

  public PseudoTextBuilder getFormatter() {
    return null;
  }

  public ParserDefinition getParserDefinition() {
    return null;
  }

  public WordsScanner getWordsScanner() {
    return null;
  }

  public boolean mayHaveReferences(IElementType token) {
    return false;
  }

  public FoldingBuilder getFoldingBuilder() {
    return null;
  }

  public PairedBraceMatcher getPairedBraceMatcher() {
    return null;
  }

  public Annotator getAnnotator() {
    return null;
  }

  public String toString() {
    return "Language: " + myID;
  }

  public StructureViewModel getStructureViewModel(final PsiElement psiElement) {
    return null;
  }
}
