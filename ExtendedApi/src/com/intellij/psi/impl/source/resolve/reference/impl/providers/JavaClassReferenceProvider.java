package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:30:38
 * To change this template use Options | File Templates.
 */
public class JavaClassReferenceProvider extends GenericReferenceProvider implements CustomizableReferenceProvider {

  public static final CustomizationKey<Boolean> RESOLVE_QUALIFIED_CLASS_NAME =
    new CustomizationKey<Boolean>(PsiBundle.message("qualified.resolve.class.reference.provider.option"));
  public static final CustomizationKey<String[]> EXTEND_CLASS_NAMES = new CustomizationKey<String[]>("EXTEND_CLASS_NAMES");
  public static final CustomizationKey<Boolean> INSTANTIATABLE = new CustomizationKey<Boolean>("INSTANTIATABLE");
  public static final CustomizationKey<Boolean> CONCRETE = new CustomizationKey<Boolean>("CONCRETE");
  public static final CustomizationKey<Boolean> NOT_INTERFACE = new CustomizationKey<Boolean>("NOT_INTERFACE");
  public static final CustomizationKey<Boolean> ADVANCED_RESOLVE = new CustomizationKey<Boolean>("RESOLVE_ONLY_CLASSES");
  public static final CustomizationKey<Boolean> JVM_FORMAT = new CustomizationKey<Boolean>("JVM_FORMAT");
  public static final CustomizationKey<String> DEFAULT_PACKAGE = new CustomizationKey<String>("DEFAULT_PACKAGE");

  @Nullable private Map<CustomizationKey, Object> myOptions;
  private boolean myAllowEmpty;
  @Nullable private final GlobalSearchScope myScope;


  public JavaClassReferenceProvider(GlobalSearchScope scope) {
    myScope = scope;
  }

  public JavaClassReferenceProvider() {
    this(null);
  }

  public <T> void setOption(CustomizationKey<T> option, T value) {
    if (myOptions == null) {
      myOptions = new THashMap<CustomizationKey, Object>();
    }
    option.putValue(myOptions, value);
  }

  @Nullable
  public GlobalSearchScope getScope() {
    return myScope;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    return getReferencesByElement(element);
  }

  public PsiReference[] getReferencesByElement(@NotNull PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = CachingReference.getManipulator(element);
    if (manipulator != null) {
      final TextRange textRange = manipulator.getRangeInElement(element);
      if (textRange.isEmpty()) return PsiReference.EMPTY_ARRAY;

      final String text = element.getText();
      final String valueString = text.substring(textRange.getStartOffset(), textRange.getEndOffset());
      return getReferencesByString(valueString, element, textRange.getStartOffset());
    }

    final String text = element.getText();
    return getReferencesByString(text, element, 0);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, int offsetInPosition) {
    if (myAllowEmpty && StringUtil.isEmpty(str)) {
      return PsiReference.EMPTY_ARRAY;
    }
    return new JavaClassReferenceSet(str, position, offsetInPosition, false, this).getAllReferences();
  }

  private static final SoftReference<List<PsiElement>> NULL_REFERENCE = new SoftReference<List<PsiElement>>(null);
  private SoftReference<List<PsiElement>> myDefaultPackageContent = NULL_REFERENCE;
  private Runnable myPackagesEraser = null;

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    if (position == null) return;
    if (hint == null || hint.shouldProcess(PsiPackage.class) || hint.shouldProcess(PsiClass.class)) {
      final List<PsiElement> cachedPackages = getDefaultPackages(position);
      for (final PsiElement psiPackage : cachedPackages) {
        if (!processor.execute(psiPackage, ResolveState.initial())) return;
      }
    }
  }

  protected List<PsiElement> getDefaultPackages(PsiElement position) {
    List<PsiElement> cachedPackages = myDefaultPackageContent.get();
    if (cachedPackages == null) {
      final List<PsiElement> psiPackages = new ArrayList<PsiElement>();
      final PsiManager manager = position.getManager();
      final BaseScopeProcessor processor = new BaseScopeProcessor() {
        public boolean execute(PsiElement element, ResolveState state) {
          psiPackages.add(element);
          return true;
        }
      };
      final String defPackageName = DEFAULT_PACKAGE.getValue(myOptions);
      if (StringUtil.isNotEmpty(defPackageName)) {
        final PsiPackage defaultPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(defPackageName);
        if (defaultPackage != null) {
          defaultPackage.processDeclarations(processor, ResolveState.initial(), position, position);
        }
      }
      final PsiPackage rootPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage("");
      if (rootPackage != null) {
        rootPackage.processDeclarations(processor, ResolveState.initial(), position, position);
      }
      if (myPackagesEraser == null) {
        myPackagesEraser = new Runnable() {
          public void run() {
            myDefaultPackageContent = NULL_REFERENCE;
          }
        };
      }
      cachedPackages = psiPackages;
      ((PsiManagerEx)manager).registerWeakRunnableToRunOnChange(myPackagesEraser);
      myDefaultPackageContent = new SoftReference<List<PsiElement>>(cachedPackages);
    }
    return cachedPackages;
  }

  public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
    myOptions = options;
  }

  @Nullable
  public Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }

  public void setAllowEmpty(final boolean allowEmpty) {
    myAllowEmpty = allowEmpty;
  }
}
