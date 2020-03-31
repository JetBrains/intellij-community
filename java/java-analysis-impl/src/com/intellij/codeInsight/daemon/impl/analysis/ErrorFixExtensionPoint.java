/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.util.xmlb.annotations.Attribute;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInsight.daemon.JavaErrorBundle.BUNDLE;

public class ErrorFixExtensionPoint extends AbstractExtensionPointBean {
  public static final ExtensionPointName<ErrorFixExtensionPoint> ERROR_FIX_EXTENSION_POINT =
    ExtensionPointName.create("com.intellij.java.error.fix");

  @Attribute("errorCode")
  public String errorCode;

  @Attribute("implementationClass")
  public String implementationClass;

  IntentionAction instantiate(PsiElement context) {
    try {
      return findExtensionClass(implementationClass).asSubclass(IntentionAction.class).getConstructor(PsiElement.class).newInstance(context);
    }
    catch (InvocationTargetException e) {
      if(e.getCause() instanceof ProcessCanceledException) {
        throw ((ProcessCanceledException)e.getCause());
      }
      throw new PluginException("Error instantiating quick-fix " + implementationClass + " (error code: " + errorCode + ")", e.getCause(), getPluginId());
    }
    catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
      throw new PluginException("Error instantiating quick-fix " + implementationClass + " (error code: " + errorCode + ")", e, getPluginId());
    }
  }

  private static volatile Map<String, List<ErrorFixExtensionPoint>> ourCodeToFix;

  @NotNull
  private static Map<String, List<ErrorFixExtensionPoint>> getCodeToFixMap() {
    Map<String, List<ErrorFixExtensionPoint>> map = ourCodeToFix;
    if (map == null) {
      ourCodeToFix = map = StreamEx.of(ERROR_FIX_EXTENSION_POINT.getExtensions()).groupingBy(fix -> fix.errorCode);
    }
    return map;
  }

  @Contract("null, _, _ -> null")
  @Nullable
  public static HighlightInfo registerFixes(@Nullable HighlightInfo info,
                                            @NotNull PsiElement context,
                                            @NotNull @PropertyKey(resourceBundle = BUNDLE) String code) {
    if (info == null) return null;
    List<ErrorFixExtensionPoint> fixes = getCodeToFixMap().get(code);
    for (ErrorFixExtensionPoint fix : fixes) {
      QuickFixAction.registerQuickFixAction(info, fix.instantiate(context));
    }
    return info;
  }
}
