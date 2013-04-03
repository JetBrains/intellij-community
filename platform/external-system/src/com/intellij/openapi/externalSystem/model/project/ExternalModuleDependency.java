package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class ExternalModuleDependency extends AbstractExternalDependency<ExternalModule> {

  public static final Comparator<ExternalModuleDependency> COMPARATOR = new Comparator<ExternalModuleDependency>() {
    @Override
    public int compare(ExternalModuleDependency o1, ExternalModuleDependency o2) {
      return Named.COMPARATOR.compare(o1.getTarget(), o2.getTarget());
    }
  };
  
  private static final long serialVersionUID = 1L;
  
  public ExternalModuleDependency(@NotNull ExternalModule ownerModule, @NotNull ExternalModule module) {
    super(ownerModule, module);
  }

  @Override
  public void invite(@NotNull ExternalEntityVisitor visitor) {
    visitor.visit(this);
  }

  @NotNull
  @Override
  public ExternalModuleDependency clone(@NotNull ExternalEntityCloneContext context) {
    ExternalModuleDependency result = new ExternalModuleDependency(getOwnerModule().clone(context), getTarget().clone(context));
    copyTo(result); 
    return result;
  }
}
