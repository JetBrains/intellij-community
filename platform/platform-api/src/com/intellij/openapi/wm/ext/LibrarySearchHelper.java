package com.intellij.openapi.wm.ext;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface LibrarySearchHelper {
    boolean isLibraryExists(@NotNull Project project);
}
