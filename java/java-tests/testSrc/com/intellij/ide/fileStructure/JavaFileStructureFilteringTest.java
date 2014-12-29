/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.fileStructure;

/**
 * @author Konstantin Bulenkov
 */
public class JavaFileStructureFilteringTest extends JavaFileStructureTestCase {
  @Override
  protected String getTestDataFolderName() {
    return "filtering";
  }

  public void testSimple()            {checkTree("f");}
  public void testReturnValue()       {checkTree("point");}
  public void testAnonymousType()     {checkTree("point");}
  public void testCamel()             {checkTree("sohe");}
  public void testCamel2()            {checkTree("soHe");}
  public void testSelectLeafFirst()   {checkTree("clear");}
  public void testSelectLeafFirst2()  {checkTree("clear");}
  public void testSelectLeafFirst3()  {checkTree("clear");}
  public void testSelectLeafFirst4()  {checkTree("clear");}

  public void testMatcher()           {checkTree("dis");}

  public void testMatcher1()          {checkTree("ico");}
  public void testMatcher2()          {checkTree("ico");}
  
  public void _testAnonymousMatcher2() {checkTree("ico");} //https://youtrack.jetbrains.com/issue/IDEABKL-6906
}
