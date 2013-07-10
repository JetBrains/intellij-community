package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class RefModuleImpl extends RefEntityImpl implements RefModule {
  private final Module myModule;

  protected RefModuleImpl(@NotNull Module module, @NotNull RefManager manager) {
    super(module.getName(), manager);
    myModule = module;
    ((RefProjectImpl)manager.getRefProject()).add(this);
  }

  @Override
  public void add(RefEntity child) {
    if (myChildren == null) {
       myChildren = new ArrayList<RefEntity>();
    }
    myChildren.add(child);

    if (child.getOwner() == null) {
      ((RefEntityImpl)child).setOwner(this);
    }
  }

  @Override
  protected void removeChild(RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
    }
  }

  @Override
  public void accept(@NotNull final RefVisitor refVisitor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        refVisitor.visitModule(RefModuleImpl.this);
      }
    });
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Override
  public boolean isValid() {
    return !myModule.isDisposed();
  }

  @Override
  public Icon getIcon(final boolean expanded) {
    return PlatformIcons.CLOSED_MODULE_GROUP_ICON; //ModuleType.get(getModule()).getIcon();
  }

  @Nullable
  public static RefEntity moduleFromName(final RefManager manager, final String name) {
    return manager.getRefModule(ModuleManager.getInstance(manager.getProject()).findModuleByName(name));
  }
}
