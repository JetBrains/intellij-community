// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.compiler;

public interface JpsValidationConfiguration {
  boolean isValidateOnBuild();

  boolean isValidatorEnabled(String validatorId);
}
