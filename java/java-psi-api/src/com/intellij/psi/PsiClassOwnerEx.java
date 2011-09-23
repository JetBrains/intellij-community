package com.intellij.psi;

import java.util.Set;

/**
 * @author peter
 */
public interface PsiClassOwnerEx extends PsiClassOwner {

  /**
   * @return a set of the short names of the top-level classes defined in this file, as this may be faster than getName() for each of getClasses()
   */
  Set<String> getClassNames();

}
