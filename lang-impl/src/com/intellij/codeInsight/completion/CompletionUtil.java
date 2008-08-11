package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageWordCompletion;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Compiler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CompletionUtil {
  public static final Key<TailType> TAIL_TYPE_ATTR = LookupItem.TAIL_TYPE_ATTR;

  private static final CompletionData ourGenericCompletionData = new CompletionData() {
    {
      final CompletionVariant variant = new CompletionVariant(PsiElement.class, TrueFilter.INSTANCE);
      variant.addCompletionFilter(TrueFilter.INSTANCE, TailType.NONE);
      registerVariant(variant);
    }
  };
  private static final CompletionData ourWordCompletionData = new WordCompletionData();

  private static HashMap<FileType, NotNullLazyValue<CompletionData>> ourCustomCompletionDatas = new HashMap<FileType, NotNullLazyValue<CompletionData>>();

  public static final @NonNls String DUMMY_IDENTIFIER = CompletionInitializationContext.DUMMY_IDENTIFIER;
  public static final @NonNls String DUMMY_IDENTIFIER_TRIMMED = DUMMY_IDENTIFIER.trim();
  public static final Key<PsiElement> ORIGINAL_KEY = Key.create("ORIGINAL_KEY");

  @NotNull
  public static <T extends PsiElement> T getOriginalElement(@NotNull T element) {
    final T data = (T)element.getUserData(ORIGINAL_KEY);
    return data != null ? data : element;
  }

  public static boolean startsWith(String text, String prefix) {
    //if (text.length() <= prefix.length()) return false;
    return toLowerCase(text).startsWith(toLowerCase(prefix));
  }

  private static String toLowerCase(String text) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    switch (settings.COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return text.toLowerCase();

      case CodeInsightSettings.FIRST_LETTER: {
        StringBuffer buffer = new StringBuffer();
        buffer.append(text.toLowerCase());
        if (buffer.length() > 0) {
          buffer.setCharAt(0, text.charAt(0));
        }
        return buffer.toString();
      }

      default:
        return text;
    }
  }

  private static boolean hasNonSoftReference(final PsiFile file, final int startOffset) {
    return isNonSoftReference(file.findReferenceAt(startOffset));
  }

  private static boolean isNonSoftReference(final PsiReference reference) {
    if (reference instanceof PsiMultiReference) {
      for (final PsiReference psiReference : ((PsiMultiReference)reference).getReferences()) {
        if (isNonSoftReference(psiReference)) return true;
      }
    }
    return reference != null && !reference.isSoft();
  }

  public static CompletionData getCompletionDataByElement(PsiElement element, final PsiFile file, final int startOffset) {

    final CompletionData mainData = getCompletionDataByFileType(file.getFileType());
    return getCompletionData(element, file, startOffset, mainData != null ? mainData : ourGenericCompletionData);
  }

  public static CompletionData getCompletionData(final PsiElement element, final PsiFile file,
                                                  final int startOffset,
                                                  final CompletionData mainCompletionData) {
    CompletionData wordCompletionData = null;
    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference == null) {
      ASTNode textContainer = element != null ? element.getNode() : null;
      while (textContainer != null) {
        final IElementType elementType = textContainer.getElementType();
        if (LanguageWordCompletion.INSTANCE.isEnabledIn(elementType) || elementType == PlainTextTokenTypes.PLAIN_TEXT) {
          wordCompletionData = ourWordCompletionData;
        }
        textContainer = textContainer.getTreeParent();
      }
    }

    if (wordCompletionData != null) return new CompositeCompletionData(mainCompletionData, wordCompletionData);
    return mainCompletionData;
  }

  public static void registerCompletionData(FileType fileType, NotNullLazyValue<CompletionData> completionData) {
    ourCustomCompletionDatas.put(fileType, completionData);
  }
  
  public static void registerCompletionData(FileType fileType, final CompletionData completionData) {
    registerCompletionData(fileType, new NotNullLazyValue<CompletionData>() {
      @NotNull
      protected CompletionData compute() {
        return completionData;
      }
    });
  }

  public static CompletionData getCompletionDataByFileType(FileType fileType) {
    for(CompletionDataEP ep: Extensions.getExtensions(CompletionDataEP.EP_NAME)) {
      if (ep.fileType.equals(fileType.getName())) {
        return ep.getHandler();
      }
    }
    final NotNullLazyValue<CompletionData> lazyValue = ourCustomCompletionDatas.get(fileType);
    return lazyValue == null ? null : lazyValue.getValue();
  }


  public static Pattern createCamelHumpsMatcher(String pattern) {
    Pattern pat = null;
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    int variant = settings.COMPLETION_CASE_SENSITIVE;
    Perl5Compiler compiler = new Perl5Compiler();

    try {
      switch (variant) {
        case CodeInsightSettings.NONE:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 0, true, true));
          break;
        case CodeInsightSettings.FIRST_LETTER:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 1, true, true));
          break;
        case CodeInsightSettings.ALL:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 0, false, false));
          break;
        default:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 1, true, false));
      }
    }
    catch (MalformedPatternException me) {
    }
    return pat;
  }


  static boolean isOverwrite(final LookupElement item, final char completionChar) {
    return completionChar != 0
      ? completionChar == Lookup.REPLACE_SELECT_CHAR
      : item instanceof LookupItem && ((LookupItem)item).getAttribute(LookupItem.OVERWRITE_ON_AUTOCOMPLETE_ATTR) != null;
  }


  public static boolean shouldShowFeature(final CompletionParameters parameters, @NonNls final String id) {
    return FeatureUsageTracker.getInstance().isToBeShown(id, parameters.getPosition().getProject());
  }

}
