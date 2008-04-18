/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.annotations;

@Pattern(PrintFormatPattern.PRINT_FORMAT)
public @interface PrintFormat {
}

// split up complex regex and workaround for IDEA-9173
class PrintFormatPattern {

    // %[argument_index$][flags][width][.precision]conversion

    // Expression is taken from java.util.Formatter.fsPattern

    @Language("RegExp")
    private static final String ARG_INDEX = "(?:\\d+\\$)?";
    @Language("RegExp")
    private static final String FLAGS = "(?:[-#+ 0,(<]*)?";
    @Language("RegExp")
    private static final String WIDTH = "(?:\\d+)?";
    @Language("RegExp")
    private static final String PRECISION = "(?:\\.\\d+)?";
    @Language("RegExp")
    private static final String CONVERSION = "(?:[tT])?(?:[a-zA-Z%])";
    @Language("RegExp")
    private static final String TEXT = "[^%]|%%";

    @Language("RegExp")
    static final String PRINT_FORMAT = "(?:" + TEXT + "|" +
            "(?:%" +
            ARG_INDEX +
            FLAGS +
            WIDTH +
            PRECISION +
            CONVERSION + ")" +
            ")*";
}