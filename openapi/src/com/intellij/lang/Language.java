package com.intellij.lang;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

  private static Map<Class<? extends Language>, Language> ourRegisteredLanguages = new HashMap<Class<? extends Language>, Language>();
  private String myID;
  public static final Language ANY = new Language("") {};
  private static final EmptyFindUsagesProvider EMPTY_FIND_USAGES_PROVIDER = new EmptyFindUsagesProvider();

  protected Language(final String ID) {
    myID = ID;
    Class<? extends Language> langClass = getClass();

    if (ourRegisteredLanguages.containsKey(langClass)) {
      LOG.error("Language '" + langClass.getName() + "' is already registered");
      return;
    }
    ourRegisteredLanguages.put(langClass, this);

    try {
      final Field langField = StdLanguages.class.getDeclaredField(ID);
      LOG.assertTrue(Modifier.isStatic(langField.getModifiers()));
      LOG.assertTrue(Modifier.isPublic(langField.getModifiers()));
      langField.set(null, this);
    }
    catch (NoSuchFieldException e) {
      // Do nothing. Not a standard file type
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
  }

  public static <T extends Language> T findInstance(Class<T> klass) {
    return (T)ourRegisteredLanguages.get(klass);
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

  public FoldingBuilder getFoldingBuilder() {
    return null;
  }

  public PairedBraceMatcher getPairedBraceMatcher() {
    return null;
  }

  public Commenter getCommenter() {
    return null;
  }

  public Annotator getAnnotator() {
    return null;
  }

  public FindUsagesProvider getFindUsagesProvider() {
    return EMPTY_FIND_USAGES_PROVIDER;
  }

  public StructureViewBuilder getStructureViewBuilder(PsiElement psiElement) {
    return null;
  }

  public String toString() {
    return "Language: " + myID;
  }

  private static class EmptyFindUsagesProvider implements FindUsagesProvider {
    public boolean mayHaveReferences(IElementType token, final short searchContext) {
      return false;
    }

    public WordsScanner getWordsScanner() {
      return null;
    }

    public boolean canFindUsagesFor(PsiElement psiElement) {
      return false;
    }

    public String getHelpId(PsiElement psiElement) {
      return null;
    }

    public String getType(PsiElement element) {
      return null;
    }

    public String getDescriptiveName(PsiElement element) {
      return null;
    }

    public String getNodeText(PsiElement element, boolean useFullName) {
      return null;
    }
  }
}
