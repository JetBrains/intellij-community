package com.intellij.psi.impl.meta;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.05.2003
 * Time: 3:31:09
 * To change this template use Options | File Templates.
 */
public class MetaRegistry extends MetaDataRegistrar {
  private static final List<MyBinding> ourBindings = new ArrayList<MyBinding>();
  private static boolean ourContributorsLoaded = false;

  private static final Key<CachedValue<PsiMetaData>> META_DATA_KEY = Key.create("META DATA KEY");

  public static void bindDataToElement(final PsiElement element, final PsiMetaData data){
    CachedValue<PsiMetaData> value =
      element.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<PsiMetaData>() {
      public Result<PsiMetaData> compute() {
        data.init(element);
        return new Result<PsiMetaData>(data, data.getDependences());
      }
    });
    element.putUserData(META_DATA_KEY, value);
  }

  public static PsiMetaData getMeta(final PsiElement element) {
    final PsiMetaData base = getMetaBase(element);
    return base != null ? base : null;
  }

  private static UserDataCache<CachedValue<PsiMetaData>, PsiElement, Object> ourCachedMetaCache =
    new UserDataCache<CachedValue<PsiMetaData>, PsiElement, Object>() {
      protected CachedValue<PsiMetaData> compute(final PsiElement element, Object p) {
        return element.getManager().getCachedValuesManager()
        .createCachedValue(new CachedValueProvider<PsiMetaData>() {
          public Result<PsiMetaData> compute() {
            try {
              ensureContributorsLoaded();
              for (final MyBinding binding : ourBindings) {
                if (binding.myFilter.isClassAcceptable(element.getClass()) && binding.myFilter.isAcceptable(element, element.getParent())) {
                  final PsiMetaData data = binding.myDataClass.newInstance();
                  data.init(element);
                  return new Result<PsiMetaData>(data, data.getDependences());
                }
              }
            } catch (IllegalAccessException iae) {
              throw new RuntimeException(iae);
            }
            catch (InstantiationException ie) {
              throw new RuntimeException(ie);
            }

            return new Result<PsiMetaData>(null, element);
          }
        }, false);
      }
    };

  private static void ensureContributorsLoaded() {
    if (!ourContributorsLoaded) {
      ourContributorsLoaded = true;
      for(MetaDataContributor contributor: Extensions.getExtensions(MetaDataContributor.EP_NAME)) {
        contributor.contributeMetaData(MetaDataRegistrar.getInstance());
      }
    }
  }
  
  @Nullable
  public static PsiMetaData getMetaBase(final PsiElement element) {
    ProgressManager.getInstance().checkCanceled();
    return ourCachedMetaCache.get(META_DATA_KEY, element, null).getValue();
  }

  public static <T extends PsiMetaData> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass, Disposable parentDisposable) {
    final MyBinding binding = new MyBinding(filter, aMetadataClass);
    addBinding(binding);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        ourBindings.remove(binding);
      }
    });
  }

  public static <T extends PsiMetaData> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass) {
    addBinding(new MyBinding(filter, aMetadataClass));
  }

  private static void addBinding(final MyBinding binding) {
    ourBindings.add(0, binding);
  }

  public <T extends PsiMetaData> void registerMetaData(ElementFilter filter, Class<T> metadataDescriptorClass) {
    addMetadataBinding(filter, metadataDescriptorClass);
  }

  private static class MyBinding {
    ElementFilter myFilter;
    Class<PsiMetaData> myDataClass;

    public <T extends PsiMetaData> MyBinding(@NotNull ElementFilter filter, @NotNull Class<T> dataClass) {
      myFilter = filter;
      myDataClass = (Class)dataClass;
    }
  }
}
