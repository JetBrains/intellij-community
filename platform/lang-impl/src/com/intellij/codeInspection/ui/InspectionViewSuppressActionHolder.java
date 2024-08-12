// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CustomSuppressableInspectionTool;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class InspectionViewSuppressActionHolder {
  private final Map<String, Map<ContextDescriptor, SuppressIntentionAction[]>> mySuppressActions =
    FactoryMap.create(__ -> new HashMap<>());
  private final Interner<Set<SuppressIntentionAction>> myActionSetInterner = Interner.createInterner();

  public synchronized SuppressIntentionAction @NotNull [] getSuppressActions(@NotNull InspectionToolWrapper wrapper, @NotNull PsiElement context) {
    ContextDescriptor descriptor = ContextDescriptor.from(context);
    if (descriptor == null) return SuppressIntentionAction.EMPTY_ARRAY;
    return mySuppressActions.get(wrapper.getShortName()).computeIfAbsent(descriptor, __ -> {
      final InspectionProfileEntry tool = wrapper.getTool();
      SuppressIntentionAction[] actions;
      if (tool instanceof CustomSuppressableInspectionTool) {
        actions = ((CustomSuppressableInspectionTool)tool).getSuppressActions(null);
      } else {
        actions = Stream.of(tool.getBatchSuppressActions(context))
          .map(fix -> SuppressIntentionActionFromFix.convertBatchToSuppressIntentionAction(fix))
          .toArray(SuppressIntentionAction[]::new);
      }
      return actions == null ? SuppressIntentionAction.EMPTY_ARRAY : actions;
    });
  }

  public synchronized @NotNull Set<SuppressIntentionAction> getSuppressActions(@NotNull InspectionToolWrapper wrapper) {
    return mySuppressActions.get(wrapper.getShortName()).values().stream().flatMap(Arrays::stream).collect(Collectors.toSet());
  }

  public Set<SuppressIntentionAction> internSuppressActions(@NotNull Set<SuppressIntentionAction> set) {
    synchronized (myActionSetInterner) {
      return myActionSetInterner.intern(set);
    }
  }

  private static final class ContextDescriptor {
    private final @NotNull Language myElementLanguage;
    private final @NotNull Language myFileBaseLanguage;
    private final @NotNull Set<Language> myFileLanguages;
    private final @Nullable ContextDescriptor myInjectionDescriptor;

    private static ContextDescriptor from(@NotNull PsiElement element) {
      return from(element, true);
    }

    private static ContextDescriptor from(@NotNull PsiElement element, boolean calculateInjectionDescriptor) {
      PsiFile file = element.getContainingFile();
      if (file == null) return null;
      FileViewProvider provider = file.getViewProvider();
      PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
      ContextDescriptor injectionDescriptor = calculateInjectionDescriptor && injectionHost != null ? from(injectionHost, false) : null;
      return new ContextDescriptor(element.getLanguage(), provider.getBaseLanguage(), provider.getLanguages(), injectionDescriptor);
    }

    private ContextDescriptor(@NotNull Language elementLanguage,
                              @NotNull Language fileBaseLanguage,
                              @NotNull Set<Language> languages,
                              @Nullable ContextDescriptor descriptor) {
      myElementLanguage = elementLanguage;
      myFileBaseLanguage = fileBaseLanguage;
      myFileLanguages = languages;
      myInjectionDescriptor = descriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ContextDescriptor that = (ContextDescriptor)o;

      if (!myElementLanguage.equals(that.myElementLanguage)) return false;
      if (!myFileBaseLanguage.equals(that.myFileBaseLanguage)) return false;
      if (!myFileLanguages.equals(that.myFileLanguages)) return false;
      if (!Objects.equals(myInjectionDescriptor, that.myInjectionDescriptor)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = myElementLanguage.hashCode();
      result = 31 * result + myFileBaseLanguage.hashCode();
      result = 31 * result + myFileLanguages.hashCode();
      result = 31 * result + (myInjectionDescriptor != null ? myInjectionDescriptor.hashCode() : 0);
      return result;
    }
  }
}
