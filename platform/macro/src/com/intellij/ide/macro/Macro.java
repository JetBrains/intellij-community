// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Represents a macro and allows to register custom macros in {@code com.intellij.macro} extension point.
 * <p>
 * A macro is an expandable variable, which can be used in various places,
 * e.g., command-line arguments for external tools and run configurations.
 * <p>
 * Example:
 * <p>
 * Macro with a name {@code ProjectFileDir} can be referenced as
 * {@code $ProjectFileDir$} in a run configuration command line and
 * is expanded to the absolute path of the current project directory,
 * when the run configuration is executed by a user.
 *
 * @see MacroManager
 * @see PathMacro
 * @see PathListMacro
 * @see MacroWithParams
 * @see SecondQueueExpandMacro
 * @see PromptingMacro
 */
public abstract class Macro {
  public static final ExtensionPointName<Macro> EP_NAME = ExtensionPointName.create("com.intellij.macro");

  public static final class ExecutionCancelledException extends Exception {
  }

  /**
   * @return the name that this macro is referenced by (without wrapping '$' characters).
   * If the name is {@code MyMacroName}, then it is referenced as {@code $MyMacroName$}.
   */
  public abstract @NonNls @NotNull String getName();

  /**
   * @return a short macro description presented in the macro selection dialog.
   * The description is displayed in a single line, next to the macro name on the macro list.
   */
  public abstract @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getDescription();
  
  public abstract @Nullable String expand(@NotNull DataContext dataContext) throws ExecutionCancelledException;

  public @Nullable String expand(@NotNull DataContext dataContext, String @NotNull ... args) throws ExecutionCancelledException{
    return expand(dataContext);
  }

  /**
   * @return preview of the expanded value displayed in the macro selection dialog.
   */
  public @Nullable String preview(@NotNull DataContext dataContext) {
    try {
      return expand(dataContext);
    }
    catch (ExecutionCancelledException ignore) {
      return null;
    }
  }

  public @Nullable TextRange findOccurence(@NotNull CharSequence s, int offset) {
    String prefix = "$" + getName();
    int start = Strings.indexOf(s, prefix, offset);
    int next = start + prefix.length();
    if (start < 0 || next >= s.length()) return null;
    return getRangeForSuffix(s, start, next);
  }

  protected @Nullable TextRange getRangeForSuffix(@NotNull CharSequence s, int start, int next) {
    return switch (s.charAt(next)) {
      case '$' -> TextRange.create(start, next + 1);
      case '(' -> {
        int end = Strings.indexOf(s, ")$", next);
        yield end < 0 ? null : TextRange.create(start, end + 2);
      }
      default -> null;
    };
  }

  public String expandOccurence(@NotNull DataContext context, @NotNull String occurence) throws ExecutionCancelledException {
    if (occurence.endsWith(")$")) {
      return expand(context, occurence.substring(occurence.indexOf('(') + 1, occurence.length() - 2));
    }
    return expand(context);
  }

  protected static @NotNull String getPath(@NotNull VirtualFile file) {
    return file.getPath().replace('/', File.separatorChar);
  }

  static @NotNull File getIOFile(@NotNull VirtualFile file) {
    return new File(getPath(file));
  }

  protected static @Nullable VirtualFile getVirtualDirOrParent(@NotNull DataContext dataContext) {
    VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (vFile != null && !vFile.isDirectory()) {
      vFile = vFile.getParent();
    }
    return vFile;
  }

  public static class Silent extends Macro {
    private final Macro myDelegate;
    private final String myValue;

    public Silent(@NotNull Macro delegate, String value) {
      myDelegate = delegate;
      myValue = value;
    }

    @Override
    public String expand(@NotNull DataContext dataContext) {
      return myValue;
    }

    @Override
    public @NotNull String getDescription() {
      return myDelegate.getDescription();
    }

    @Override
    public @NotNull String getName() {
      return myDelegate.getName();
    }
  }
}
