/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

/**
 * Is intended to hold specific java formatting tests for <code>'Place on New Line'</code> settings (
 * <code>Project Settings - Code Style - Alignment and Braces - Place on New Line</code>).
 *
 * @author Denis Zhdanov
 * @since Apr 28, 2010 12:12:13 PM
 */
public class JavaFormatterNewLineTest extends AbstractJavaFormattingTest {

    public void testAutomaticElseUnwrapping() throws Exception {
        getSettings().ELSE_ON_NEW_LINE = false;
        getSettings().KEEP_LINE_BREAKS = true;

        // Inspired by IDEA-47809
        doMethodTest(
                
                "if (b) {\n" +
                "}\n" +
                "else {\n" +
                "}",

                "if (b) {\n" +
                "} else {\n" +
                "}"
        );
    }
    
    public void testAutomaticCatchUnwrapping() throws Exception {
        getSettings().CATCH_ON_NEW_LINE = false;
        getSettings().KEEP_LINE_BREAKS = true;

        // Inspired by IDEA-47809
        doMethodTest(

                "try {\n" +
                "}\n" +
                "catch (Exception e) {\n" +
                "}",

                "try {\n" +
                "} catch (Exception e) {\n" +
                "}"
        );
    }

    public void testAutomaticFinallyUnwrapping() throws Exception {
        getSettings().FINALLY_ON_NEW_LINE = false;
        getSettings().KEEP_LINE_BREAKS = true;

        // Inspired by IDEA-47809
        doMethodTest(

                "try {\n" +
                "}\n" +
                "finally {\n" +
                "}",

                "try {\n" +
                "} finally {\n" +
                "}"
        );
    }
    
    public void testAutomaticCatchFinallyUnwrapping() throws Exception {
        getSettings().CATCH_ON_NEW_LINE = false;
        getSettings().FINALLY_ON_NEW_LINE = false;
        getSettings().KEEP_LINE_BREAKS = true;

        // Inspired by IDEA-47809
        doMethodTest(

                "try {\n" +
                "}\n" +
                "catch (Exception e) {\n" +
                "}\n" +
                "finally {\n" +
                "}",

                "try {\n" +
                "} catch (Exception e) {\n" +
                "} finally {\n" +
                "}"
        );
    }
}
