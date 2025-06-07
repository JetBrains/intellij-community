// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class IStubElementType<StubT extends StubElement<?>, PsiT extends PsiElement> extends IElementType implements StubSerializer<StubT> {
  private static final Set<String> NOT_INITIALIZED_SET = Collections.emptySet();

  private static volatile Set<String> lazyExternalIds = NOT_INITIALIZED_SET;

  public IStubElementType(@NotNull @NonNls String debugName, @Nullable Language language) {
    super(debugName, language);
    if (isInitialized() && !isLazilyRegistered()) {
      Logger.getInstance(IStubElementType.class)
        .error("All stub element types should be created before index initialization is complete.\n" +
               "Please add the " + getClass() + " with external ID " + getExternalId() + " containing stub element type constants to \"stubElementTypeHolder\" extension.\n" +
               "Registered extensions: " + StubElementTypeHolderEP.EP_NAME.getExtensionList() + "\n" +
               "Registered lazy ids: " +
               lazyExternalIds);
    }
  }

  public static void checkNotInstantiatedTooLate(@NotNull Class<?> aClass) {
    if (isInitialized()) {
      Logger.getInstance(IStubElementType.class)
        .error("All stub element types should be created before index initialization is complete.\n" +
               "Please add the " + aClass + " containing stub element type constants to \"stubElementTypeHolder\" extension.\n" +
               "Registered extensions: " + StubElementTypeHolderEP.EP_NAME.getExtensionList());
    }
  }

  private static boolean isInitialized() {
    return lazyExternalIds != NOT_INITIALIZED_SET;
  }

  private boolean isLazilyRegistered() {
    try {
      return lazyExternalIds.contains(getExternalId());
    }
    catch (Throwable e) {
      // "getExternalId" might throw when called from constructor, if it accesses subclass fields.
      // Lazily-registered types have a contract that their "getExternalId" doesn't throw like this,
      // so getting an exception here is a sign that someone indeed creates their stub type after StubElementTypeHolderEP initialization.
      return false;
    }
  }

  @ApiStatus.Internal
  public static void dropRegisteredTypes() {
    lazyExternalIds = NOT_INITIALIZED_SET;
  }

  @ApiStatus.Internal
  public static @NotNull @Unmodifiable List<StubFieldAccessor> loadRegisteredStubElementTypes() {
    List<StubFieldAccessor> result = new ArrayList<>();

    Logger logger = Logger.getInstance(IStubElementType.class);
    List<String> debugStr = logger.isDebugEnabled() ? new ArrayList<>() : null;

    StubElementTypeHolderEP.EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      int accessorCount = bean.initializeOptimized(pluginDescriptor, result);
      if (debugStr != null) {
        debugStr.add(accessorCount + " in " + bean.holderClass);
      }
      return Unit.INSTANCE;
    });

    if (debugStr != null) {
      logger.debug("Lazy stub element types loaded: " + StringUtil.join(debugStr, ", "));
    }

    lazyExternalIds = ContainerUtil.map2Set(result, accessor -> accessor.externalId);
    return result;
  }

  public abstract PsiT createPsi(@NotNull StubT stub);

  public abstract @NotNull StubT createStub(@NotNull PsiT psi, StubElement<? extends PsiElement> parentStub);

  public boolean shouldCreateStub(ASTNode node) {
    return true;
  }
}
