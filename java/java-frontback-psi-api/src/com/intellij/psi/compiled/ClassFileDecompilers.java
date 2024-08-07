// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.compiled;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
 * An API to extend default IDEA .class file decompiler and handle files compiled from sources other than Java.
 */
@Service
public final class ClassFileDecompilers {
  /**
   * Actual implementations should extend either {@link Light} or {@link Full} classes -
   * those that don't are silently ignored.
   */
  public interface Decompiler {
    boolean accepts(@NotNull VirtualFile file);
  }

  /**
   * <p>"Light" decompilers are intended for augmenting file text constructed by standard IDEA decompiler
   * without changing it's structure - i.e. providing additional information in comments,
   * or replacing standard "compiled code" method body comment with something more meaningful.</p>
   *
   * <p>If a plugin by somewhat reason cannot decompile a file it can throw {@link Light.CannotDecompileException}
   * and thus make IDEA to fall back to a built-in decompiler implementation.</p>
   *
   * <p>Plugins registering extension of this type normally should accept all files and use {@code order="last"}
   * attribute to avoid interfering with other decompilers.</p>
   */
  public abstract static class Light implements Decompiler {
    public static class CannotDecompileException extends RuntimeException {
      public CannotDecompileException(String message, Throwable cause) {
        super(message, cause);
      }
    }

    @NotNull
    public abstract CharSequence getText(@NotNull VirtualFile file) throws CannotDecompileException;
  }

  /**
   * <p>"Full" decompilers are designed to provide extended support for languages significantly different from Java.
   * Extensions of this type should take care of building file stubs and properly indexing them -
   * in return they have an ability to represent decompiled file in a way natural for original language.</p>
   */
  public abstract static class Full implements Decompiler {
    @NotNull
    public abstract ClsStubBuilder getStubBuilder();

    /**
     * <h5>Notes for implementers</h5>
     *
     * <p>1. Return a correct language from {@link FileViewProvider#getBaseLanguage()}.</p>
     *
     * <p>2. This method is called for both PSI file construction and obtaining document text.
     * In the latter case the PsiManager is based on default project, and the only method called
     * on a resulting view provider is {@link FileViewProvider#getContents()}.</p>
     *
     * <p>3. A language compiler may produce auxiliary .class files which should be handled as part of their parent classes.
     * A standard practice is to hide such files by returning {@code null} from
     * {@link FileViewProvider#getPsi(com.intellij.lang.Language)}.</p>
     */
    @NotNull
    public abstract FileViewProvider createFileViewProvider(@NotNull VirtualFile file, @NotNull PsiManager manager, boolean physical);
  }

  public static ClassFileDecompilers getInstance() {
    return ApplicationManager.getApplication().getService(ClassFileDecompilers.class);
  }

  public final ExtensionPointName<Decompiler> EP_NAME = new ExtensionPointName<>("com.intellij.psi.classFileDecompiler");

  private ClassFileDecompilers() {
    Runnable runnable = () -> BinaryFileTypeDecompilers.getInstance().notifyDecompilerSetChange();
    EP_NAME.addChangeListener(runnable, null);
  }

  @SuppressWarnings("unchecked")
  public <D extends Decompiler> D find(@NotNull VirtualFile file, @NotNull Class<D> decompilerClass) {
    return (D)EP_NAME.findFirstSafe(d -> decompilerClass.isInstance(d) && d.accepts(file));
  }
}
