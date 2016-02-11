/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.ide.actions.as.CreateNewClassDialogValidatorExImpl;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CreateNewClassDialogValidatorExImplTest {
  private CreateNewClassDialogValidatorExImpl validator;

  @Before
  public void initValidator() {
    validator = new CreateNewClassDialogValidatorExImpl(null);
  }

  @Test
  public void checkInterfacesSingleName() {
    assertTrue(validator.checkInterfaces("_abc123_XYZ_"));
  }

  @Test
  public void checkInterfacesMultipleNames() {
    assertTrue(validator.checkInterfaces("_abc123, XYZ456_, abcdef"));
  }

  @Test
  public void checkInterfacesEmpty() {
    assertTrue(validator.checkInterfaces(""));
  }

  @Test
  public void checkInterfacesDuplicateNames() {
    assertFalse(validator.checkInterfaces("abc, abc"));
  }

  @Test
  public void checkInterfacesLeadingNumber() {
    assertFalse(validator.checkInterfaces("8a"));
  }

  @Test
  public void checkInterfacesDashInName() {
    assertFalse(validator.checkInterfaces("a-b"));
  }

  @Test
  public void checkInterfacesTrailingComma() {
    assertFalse(validator.checkInterfaces("a, b,"));
  }

  @Test
  public void checkInterfacesLeadingComma() {
    assertFalse(validator.checkInterfaces(", a, b"));
  }

  @Test
  public void checkInterfacesSpaceSeparated() {
    assertFalse(validator.checkInterfaces("a b"));
  }

  @Test
  public void checkPackageGood() {
    assertTrue(validator.checkPackage("_abc123.xyz.ABC.ABC"));
  }

  @Test
  public void checkPackageGoodSpaces() {
    assertTrue(validator.checkPackage("_abc123 .xyz . ABC. ABC"));
  }

  @Test
  public void checkPackageEmpty() {
    assertFalse(validator.checkPackage(""));
  }

  @Test
  public void checkPackageLeadingNumber() {
    assertFalse(validator.checkPackage("8_abc123.xyz.ABC.ABC"));
  }

  @Test
  public void checkPackageLeadingDot() {
    assertFalse(validator.checkPackage("._abc123.xyz.ABC.ABC"));
  }

  @Test
  public void checkPackageTrailingDot() {
    assertFalse(validator.checkPackage("_abc123.xyz.ABC.ABC."));
  }

  @Test
  public void checkPackageDash() {
    assertFalse(validator.checkPackage("_abc123-xyz.ABC.ABC"));
  }

  @Test
  public void checkPackageComma() {
    assertFalse(validator.checkPackage("_abc123,xyz.ABC.ABC"));
  }

  @Test
  public void checkPackageBadSpace() {
    assertFalse(validator.checkPackage("_abc123 xyz.ABC.ABC"));
  }

  @Test
  public void checkSuperclassGood() {
    assertTrue(validator.checkSuperclass("_abc123_XYZ__"));
  }

  @Test
  public void checkSuperclassEmpty() {
    assertTrue(validator.checkSuperclass(""));
  }

  @Test
  public void checkSuperclassLeadingNumber() {
    assertFalse(validator.checkSuperclass("0_abc123_XYZ__"));
  }

  @Test
  public void checkSuperclassDash() {
    assertFalse(validator.checkSuperclass("_abc123-XYZ__"));
  }

  @Test
  public void checkSuperclassComma() {
    assertFalse(validator.checkSuperclass("_abc123,XYZ_"));
  }

  @Test
  public void checkSuperclassSpace() {
    assertFalse(validator.checkSuperclass("_abc123_XYZ _"));
  }
}
