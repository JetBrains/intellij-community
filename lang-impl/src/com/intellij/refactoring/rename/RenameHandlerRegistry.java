package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.containers.HashSet;

import java.util.Set;

/**
 * @author dsl
 */
public class RenameHandlerRegistry {
  private Set<RenameHandler> myHandlers  = new HashSet<RenameHandler>();
  private static final RenameHandlerRegistry INSTANCE = new RenameHandlerRegistry();
  private PsiElementRenameHandler myDefaultElementRenameHandler;

  public static RenameHandlerRegistry getInstance() {
    return INSTANCE;
  }

  private RenameHandlerRegistry() {
    // should be checked last
    myDefaultElementRenameHandler = new PsiElementRenameHandler();
  }

  public boolean hasAvailableHandler(DataContext dataContext) {
    for (RenameHandler renameHandler : Extensions.getExtensions(RenameHandler.EP_NAME)) {
      if (renameHandler.isAvailableOnDataContext(dataContext)) return true;
    }
    for (RenameHandler renameHandler : myHandlers) {
      if (renameHandler.isAvailableOnDataContext(dataContext)) return true;
    }
    return myDefaultElementRenameHandler.isAvailableOnDataContext(dataContext);
  }

  public RenameHandler getRenameHandler(DataContext dataContext) {
    for (RenameHandler renameHandler : Extensions.getExtensions(RenameHandler.EP_NAME)) {
      if (renameHandler.isRenaming(dataContext)) return renameHandler;
    }
    for (RenameHandler renameHandler : myHandlers) {
      if (renameHandler.isRenaming(dataContext)) return renameHandler;
    }
    return myDefaultElementRenameHandler.isRenaming(dataContext) ? myDefaultElementRenameHandler : null;
  }

  public void registerHandler(RenameHandler handler) {
    myHandlers.add(handler);
  }
}
