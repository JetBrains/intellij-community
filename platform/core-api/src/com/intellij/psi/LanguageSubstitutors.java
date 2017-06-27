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

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A utility class used to query the language for PSI from {@link LanguageSubstitutor} extensions.
 */
public final class LanguageSubstitutors extends LanguageExtension<LanguageSubstitutor> {
  public static final LanguageSubstitutors INSTANCE = new LanguageSubstitutors();
  private static final Logger LOG = Logger.getInstance(LanguageSubstitutors.class);
  private static final Key<Key<Language>> PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY = Key.create("PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY");
  private static final AtomicBoolean REQUESTS_DRAIN_NEEDED = new AtomicBoolean(true);
  private static final ConcurrentMap<VirtualFile, SubstitutionInfo> ourReparsingRequests = ContainerUtil.newConcurrentMap();

  private LanguageSubstitutors() {
    super("com.intellij.lang.substitutor");
  }

  /**
   * Queries all applicable language substitutors and returns the substituted language, or {@code lang} argument if
   * no substitutor has returned anything.
   */
  @NotNull
  public Language substituteLanguage(@NotNull Language lang, @NotNull VirtualFile file, @NotNull Project project) {
    for (LanguageSubstitutor substitutor : forKey(lang)) {
      Language language = substitutor.getLanguage(file, project);
      if (language != null) {
        processLanguageSubstitution(file, lang, language, project);
        return language;
      }
    }
    return lang;
  }

  private static void processLanguageSubstitution(@NotNull final VirtualFile file,
                                                  @NotNull Language originalLang,
                                                  @NotNull final Language substitutedLang,
                                                  @NotNull Project project) {
    if (file instanceof VirtualFileWindow) {
      // Injected files are created with substituted language, no need to reparse:
      //   com.intellij.psi.impl.source.tree.injected.MultiHostRegistrarImpl#doneInjecting
      return;
    }
    Key<Language> projectKey = getOrCreateProjectKey(project);
    Language prevSubstitutedLang = projectKey.get(file);
    final Language prevLang = ObjectUtils.notNull(prevSubstitutedLang, originalLang);
    if (!prevLang.is(substitutedLang)) {
      if (file.replace(projectKey, prevSubstitutedLang, substitutedLang)) {
        if (prevSubstitutedLang == null) {
          return; // no need to reparse for the first language substitution
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          return;
        }
        requestReparsing(file, prevLang, substitutedLang);
      }
    }
  }

  @NotNull
  private static Key<Language> getOrCreateProjectKey(@NotNull Project project) {
    Key<Language> key = PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY.get(project);
    if (key == null) {
      synchronized (PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY) {
        key = PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY.get(project);
        if (key == null) {
          key = new Key<>("Substituted lang key for " + project.getName());
          PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY.set(project, key);
        }
      }
    }
    return key;
  }

  private static void requestReparsing(@NotNull VirtualFile file, @NotNull Language prevLang, @NotNull Language substitutedLang) {
    ourReparsingRequests.put(file, new SubstitutionInfo(prevLang, substitutedLang));
    if (REQUESTS_DRAIN_NEEDED.compareAndSet(true, false)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        REQUESTS_DRAIN_NEEDED.set(true);
        List<Map.Entry<VirtualFile, SubstitutionInfo>> set = ContainerUtil.newArrayList(ourReparsingRequests.entrySet());
        List<VirtualFile> files = ContainerUtil.newArrayListWithCapacity(set.size());
        int id = 1;
        for (Map.Entry<VirtualFile, SubstitutionInfo> entry : set) {
          VirtualFile f = entry.getKey();
          SubstitutionInfo info = entry.getValue();
          ourReparsingRequests.remove(f);
          if (f.isValid()) {
            LOG.info("Reparsing " + f.getPath() + " because of language substitution " +
                     info.myPrevLang.getID() + "->" + info.mySubstitutedLang.getID() + ", #" + (id++));
            files.add(f);
          }
        }
        if (files.size() > 0) {
          FileContentUtilCore.reparseFiles(files);
        }
      }, ModalityState.defaultModalityState());
    }
  }

  public static void cancelReparsing(@NotNull VirtualFile file) {
    ourReparsingRequests.remove(file);
  }

  private static class SubstitutionInfo {
    private final Language myPrevLang;
    private final Language mySubstitutedLang;

    public SubstitutionInfo(@NotNull Language prevLang, @NotNull Language substitutedLang) {
      myPrevLang = prevLang;
      mySubstitutedLang = substitutedLang;
    }
  }
}
