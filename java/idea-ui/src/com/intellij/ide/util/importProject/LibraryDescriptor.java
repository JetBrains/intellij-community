/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.importProject;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public class LibraryDescriptor {
  
  private String myName;
  private final Collection<File> myJars;

  public LibraryDescriptor(String name, Collection<File> jars) {
    myName = name;
    myJars = jars;
  }

  public String getName() {
    return myName != null? myName : "";
  }

  public void setName(final String name) {
    myName = name;
  }

  public Collection<File> getJars() {
    return Collections.unmodifiableCollection(myJars);
  }
  
  public void addJars(Collection<File> jars) {
    myJars.addAll(jars);
  }
  
  public void removeJars(Collection<File> jars) {
    myJars.removeAll(jars);
  }

  public String toString() {
    return "Lib[" + myName + "]";
  }
}
