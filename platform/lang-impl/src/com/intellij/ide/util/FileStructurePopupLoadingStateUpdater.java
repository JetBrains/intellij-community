package com.intellij.ide.util;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface FileStructurePopupLoadingStateUpdater {
  default void installUpdater(@NotNull Consumer<Integer> consumer, @NotNull Project project, @NotNull StructureViewModel treeModel) {
    int delayMillis = 300;
    consumer.accept(delayMillis);
  }
}