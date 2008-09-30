package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
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
  public static final CustomizationKey<Pair<String, ClassKind>> CLASS_TEMPLATE = new CustomizationKey<Pair<String, ClassKind>>("CLASS_TEMPLATE");
  public static final CustomizationKey<Boolean> INSTANTIATABLE = new CustomizationKey<Boolean>("INSTANTIATABLE");
  public static final CustomizationKey<Boolean> CONCRETE = new CustomizationKey<Boolean>("CONCRETE");
  public static final CustomizationKey<Boolean> NOT_INTERFACE = new CustomizationKey<Boolean>("NOT_INTERFACE");
  public static final CustomizationKey<Boolean> NOT_ENUM= new CustomizationKey<Boolean>("NOT_ENUM");
  public static final CustomizationKey<Boolean> ADVANCED_RESOLVE = new CustomizationKey<Boolean>("RESOLVE_ONLY_CLASSES");
  public static final CustomizationKey<Boolean> JVM_FORMAT = new CustomizationKey<Boolean>("JVM_FORMAT");
  public static final CustomizationKey<Boolean> ALLOW_DOLLAR_NAMES = new CustomizationKey<Boolean>("ALLOW_DOLLAR_NAMES");
  public static final CustomizationKey<String> DEFAULT_PACKAGE = new CustomizationKey<String>("DEFAULT_PACKAGE");

  @Nullable private Map<CustomizationKey, Object> myOptions;
  private boolean myAllowEmpty;
  @Nullable private final GlobalSearchScope myScope;
  private final CachedValue<List<PsiElement>> myDefaltPackages;


  public JavaClassReferenceProvider(GlobalSearchScope scope, final Project project) {
    myScope = scope;
    myDefaltPackages = PsiManager.getInstance(project).getCachedValuesManager().createCachedValue(new CachedValueProvider<List<PsiElement>>() {
      public Result<List<PsiElement>> compute() {
        final List<PsiElement> psiPackages = new ArrayList<PsiElement>();
        final String defPackageName = DEFAULT_PACKAGE.getValue(myOptions);
        if (StringUtil.isNotEmpty(defPackageName)) {
          final PsiPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage(defPackageName);
          if (defaultPackage != null) {
            psiPackages.addAll(getSubPackages(defaultPackage));
          }
        }
        final PsiPackage rootPackage = JavaPsiFacade.getInstance(project).findPackage("");
        if (rootPackage != null) {
          psiPackages.addAll(getSubPackages(rootPackage));
        }
        return Result.createSingleDependency(psiPackages, PsiModificationTracker.MODIFICATION_COUNT);
      }
    }, false);
  }

  public JavaClassReferenceProvider(final Project project) {
    this(null, project);
  }

  public <T> void setOption(CustomizationKey<T> option, T value) {
    if (myOptions == null) {
      myOptions = new THashMap<CustomizationKey, Object>();
    }
    option.putValue(myOptions, value);
  }

  @Nullable
  public <T> T getOption(CustomizationKey<T> option) {
    return myOptions == null ? null : (T)myOptions.get(option);
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
    final int offsetInElement = ElementManipulators.getOffsetInElement(element);
    final String text = ElementManipulators.getValueText(element);
    return getReferencesByString(text, element, offsetInElement);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, int offsetInPosition) {
    if (myAllowEmpty && StringUtil.isEmpty(str)) {
      return PsiReference.EMPTY_ARRAY;
    }
    boolean allowDollars = Boolean.TRUE.equals(getOption(ALLOW_DOLLAR_NAMES));
    return new JavaClassReferenceSet(str, position, offsetInPosition, allowDollars, this).getAllReferences();
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    if (position == null) return;
    if (hint == null || hint.shouldProcess(PsiPackage.class) || hint.shouldProcess(PsiClass.class)) {
      final List<PsiElement> cachedPackages = getDefaultPackages();
      for (final PsiElement psiPackage : cachedPackages) {
        if (!processor.execute(psiPackage, ResolveState.initial())) return;
      }
    }
  }

  protected List<PsiElement> getDefaultPackages() {
    return myDefaltPackages.getValue();
  }

  private static Collection<PsiPackage> getSubPackages(final PsiPackage defaultPackage) {
    return ContainerUtil.mapNotNull(defaultPackage.getSubPackages(), new NullableFunction<PsiPackage, PsiPackage>() {
      public PsiPackage fun(final PsiPackage psiPackage) {
        final String packageName = psiPackage.getName();
        return JavaPsiFacade.getInstance(psiPackage.getProject()).getNameHelper()
            .isIdentifier(packageName, PsiUtil.getLanguageLevel(psiPackage)) ? psiPackage : null;
      }
    });
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
