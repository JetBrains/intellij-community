/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.containers.ContainerUtil;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * @author peter
 */
class CachedValueChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.CachedValueChecker");
  private static final boolean DO_CHECKS = ApplicationManager.getApplication().isUnitTestMode();
  private static Set<Class> ourCheckedClasses = ContainerUtil.newConcurrentSet();

  static void checkProvider(CachedValueProvider provider, UserDataHolder userDataHolder) {
    if (!DO_CHECKS) return;

    Class<? extends CachedValueProvider> providerClass = provider.getClass();
    if (!ourCheckedClasses.add(providerClass)) return;

    for (Field field : providerClass.getDeclaredFields()) {
      try {
        field.setAccessible(true);
        Object o = field.get(provider);
        if (o instanceof PsiElement && o != userDataHolder) {
          LOG.error("Incorrect CachedValue use. Provider references PSI, causing memory leaks and possible invalid element access: field " + field.getName() + " of " + provider);
          return;
        }
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }
  }
}
