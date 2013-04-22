/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class ReferenceProviderType {
  @NonNls public static final String EP_NAME = "com.intellij.referenceProviderType";
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ReferenceProviderType");
  private static final KeyedExtensionCollector<PsiReferenceProvider,ReferenceProviderType> COLLECTOR =
    new KeyedExtensionCollector<PsiReferenceProvider, ReferenceProviderType>(EP_NAME) {
    @Override
    protected String keyToString(final ReferenceProviderType key) {
      return key.myId;
    }
  };
  private final String myId;

  public ReferenceProviderType(@NonNls @NotNull String id) {
    myId = id;
  }

  @NotNull
  public PsiReferenceProvider getProvider() {
    final List<PsiReferenceProvider> list = COLLECTOR.forKey(this);
    LOG.assertTrue(list.size() == 1, list.toString());
    return list.get(0);
  }

  public String toString() {
    return myId;
  }

}
