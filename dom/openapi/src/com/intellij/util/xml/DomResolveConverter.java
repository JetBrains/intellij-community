/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.WeakFactoryMap;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.LocalQuickFix;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author peter
 */
public class DomResolveConverter<T extends DomElement> extends ResolvingConverter<T>{
  private static final FactoryMap<Class<? extends DomElement>,DomResolveConverter> ourCache = new FactoryMap<Class<? extends DomElement>, DomResolveConverter>() {
    @NotNull
    protected DomResolveConverter create(final Class<? extends DomElement> key) {
      return new DomResolveConverter(key);
    }
  };
  private final WeakFactoryMap<DomElement, CachedValue<Map<String, DomElement>>> myResolveCache = new WeakFactoryMap<DomElement, CachedValue<Map<String, DomElement>>>() {
    @NotNull
    protected CachedValue<Map<String, DomElement>> create(final DomElement scope) {
      final DomManager domManager = scope.getManager();
      final Project project = domManager.getProject();
      return PsiManager.getInstance(project).getCachedValuesManager().createCachedValue(new CachedValueProvider<Map<String, DomElement>>() {
        public Result<Map<String, DomElement>> compute() {
          final Map<String, DomElement> map = new THashMap<String, DomElement>();
          scope.acceptChildren(new DomElementVisitor() {
            public void visitDomElement(DomElement element) {
              if (myClass.isInstance(element)) {
                final String name = element.getGenericInfo().getElementName(element);
                if (name != null && !map.containsKey(name)) {
                  map.put(name, element);
                }
              } else {
                element.acceptChildren(this);
              }
            }
          });
          return new Result<Map<String, DomElement>>(map, getPsiFiles(scope));
        }
      }, false);
    }
  };

  private final Class<T> myClass;

  private static XmlFile[] getPsiFiles(DomElement element) {
    final Collection<DomElement> collection = ModelMergerUtil.getImplementations(element, DomElement.class);
    return ContainerUtil.map2Array(collection, XmlFile.class, new Function<DomElement, XmlFile>() {
      public XmlFile fun(final DomElement s) {
        return s.getRoot().getFile();
      }
    });
  }

  public DomResolveConverter(final Class<T> aClass) {
    myClass = aClass;
  }

  public static <T extends DomElement> DomResolveConverter<T> createConverter(Class<T> aClass) {
    return ourCache.get(aClass);
  }

  public final T fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    return (T) myResolveCache.get(getResolvingScope(context)).getValue().get(s);
  }

  private static DomElement getResolvingScope(final ConvertContext context) {
    final DomElement invocationElement = context.getInvocationElement();
    return invocationElement.getManager().getResolvingScope((GenericDomValue)invocationElement);
  }

  public String getErrorMessage(final String s, final ConvertContext context) {
    return CodeInsightBundle.message("error.cannot.resolve.0.1", ElementPresentationManager.getTypeName(myClass), s);
  }

  public final String toString(final T t, final ConvertContext context) {
    if (t == null) return null;
    return t.getGenericInfo().getElementName(t);
  }

  @NotNull
  public Collection<? extends T> getVariants(final ConvertContext context) {
    final DomElement reference = context.getInvocationElement();
    final DomElement scope = reference.getManager().getResolvingScope((GenericDomValue)reference);
    return (Collection<T>)myResolveCache.get(scope).getValue().values();
  }

  /**
   * @param context context
   * @return LocalQuickFix'es to correct non-resolved value (e.g. 'create from usage')
   */
  public LocalQuickFix[] getQuickFixes(final ConvertContext context) {
    return new LocalQuickFix[0];
  }
}
