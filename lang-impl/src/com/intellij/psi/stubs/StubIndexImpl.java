/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class StubIndexImpl extends StubIndex implements ApplicationComponent {
  public <Key, Psi extends PsiElement> Collection<StubElement<Psi>> get(final StubIndexKey<Key, Psi> index, final Key key) {
    return null;
  }

  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}