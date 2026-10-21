/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.ide.fileStructure;

import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
public class JavaFileStructureFilteringTest extends JavaFileStructureTestCase {
  @Override
  protected String getTestDataFolderName() {
    return "filtering";
  }

  @Test public void testSimple()            {checkTree("f");}
  @Test public void testReturnValue()       {checkTree("point");}
  @Test public void testAnonymousType()     {checkTree("point");}
  @Test public void testCamel()             {checkTree("sohe");}
  @Test public void testCamel2()            {checkTree("soHe");}
  @Test public void testSelectLeafFirst()   {checkTree("clear");}
  @Test public void testSelectLeafFirst2()  {checkTree("clear");}
  @Test public void testSelectLeafFirst3()  {checkTree("clear");}
  @Test public void testSelectLeafFirst4()  {checkTree("clear");}

  @Test public void testResolveByInitialImport() {checkTree("dis");}
  @Test public void testResolveByInitialClass()  {checkTree("dis");}
  @Test public void testResolveByBetterMethod()  {checkTree("dis");}
  @Test public void testResolveByWorseMethod()   {checkTree("dis");}
  @Test public void testResolveByHiddenMethod()  {checkTree("dis");}

  @Test public void testResolveChildrenBeforeSiblings()      {checkTree("dis");}
  @Test public void testResolveSiblingsBeforeGrandChildren() {checkTree("dis");}
  @Test public void testResolveGrandChildrenBefore()         {checkTree("dis");}
  @Test public void testResolveNoSiblingsAndChildren()       {checkTree("dis");}

  @Test public void testMatcher()           {checkTree("dis");}

  @Test public void testMatcher1()          {checkTree("ico");}
  @Test public void testMatcher2()          {checkTree("ico");}
  
  public void _testAnonymousMatcher2() {checkTree("ico");} //https://youtrack.jetbrains.com/issue/IDEABKL-6906
}
