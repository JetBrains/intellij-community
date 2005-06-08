package com.intellij.lang;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
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

  private static SurroundDescriptor[] EMPTY_SURROUND_DESCRIPTORS_ARRAY = new SurroundDescriptor[0];
  private static Map<Class<? extends Language>, Language> ourRegisteredLanguages = new HashMap<Class<? extends Language>, Language>();
  private String myID;
  private String[] myMimeType;
  public static final Language ANY = new Language("", "") {};
  private static final EmptyFindUsagesProvider EMPTY_FIND_USAGES_PROVIDER = new EmptyFindUsagesProvider();

  protected Language(String id) {
    this(id, "");
  }

  protected Language(final String ID, final String... mime) {
    myID = ID;
    myMimeType = mime;
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
  
  public static Collection<Language> getRegisteredLanguages() {
    return Collections.unmodifiableCollection(ourRegisteredLanguages.values());
  }

  public static <T extends Language> T findInstance(Class<T> klass) {
    return (T)ourRegisteredLanguages.get(klass);
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new PlainSyntaxHighlighter();
  }

  public FormattingModelBuilder getFormattingModelBuilder(){
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

  public RefactoringSupportProvider getRefactoringSupportProvider() {
    return null;
  }

  @NotNull public SurroundDescriptor[] getSurroundDescriptors() {
    return EMPTY_SURROUND_DESCRIPTORS_ARRAY;
  }

  public String toString() {
    return "Language: " + myID;
  }

  public String[] getMimeType(){
    return myMimeType;
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
      return "";
    }

    public String getDescriptiveName(PsiElement element) {
      //do not return null
      return element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : "";
    }

    public String getNodeText(PsiElement element, boolean useFullName) {
      //do not return null
      return element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : "";
    }
  }
}
