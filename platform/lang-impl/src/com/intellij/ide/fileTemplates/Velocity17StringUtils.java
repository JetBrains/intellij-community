// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

// JB: Copied from Velocity 1.7 for user template backward compatibility (https://youtrack.jetbrains.com/issue/IDEA-107709)

/**
 * This class provides some methods for dynamically
 * invoking methods in objects, and some string
 * manipulation methods used by torque. The string
 * methods will soon be moved into the turbine
 * string utilities class.
 *
 *  @author <a href="mailto:jvanzyl@apache.org">Jason van Zyl</a>
 *  @author <a href="mailto:dlr@finemaltcoding.com">Daniel Rall</a>
 *  @version $Id: StringUtils.java 685685 2008-08-13 21:43:27Z nbubna $
 */
@SuppressWarnings({"unused", "StringBufferMayBeStringBuilder", "ForLoopReplaceableByForEach", "MissingDeprecatedAnnotation",
  "UnusedAssignment", "StringToUpperCaseOrToLowerCaseWithoutLocale", "unchecked", "SSBasedInspection", "UseOfObsoleteCollectionType",
  "SwitchStatementWithTooFewBranches", "AssignmentToForLoopParameter", "IOResourceOpenedButNotSafelyClosed", "CallToPrintStackTrace",
  "UnnecessaryFullyQualifiedName", "ToArrayCallWithZeroLengthArrayArgument", "FinalStaticMethod"})
public class Velocity17StringUtils
{
    /**
     * Line separator for the OS we are operating on.
     */
    private static final String EOL = System.getProperty("line.separator");

    /**
     * Concatenates a list of objects as a String.
     *
     * @param list The list of objects to concatenate.
     * @return     A text representation of the concatenated objects.
     */
    public String concat(List list)
    {
        StringBuffer sb = new StringBuffer();
        int size = list.size();

        for (int i = 0; i < size; i++)
        {
            sb.append(list.get(i).toString());
        }
        return sb.toString();
    }

    /**
     * Return a package name as a relative path name
     *
     * @param pckge package name to convert to a directory.
     * @return String directory path.
     */
    static public String getPackageAsPath(String pckge)
    {
        return pckge.replace( '.', File.separator.charAt(0) ) + File.separator;
    }

    /**
     * <p>
     * Remove underscores from a string and replaces first
     * letters with capitals.  Other letters are changed to lower case.
     * </p>
     *
     * <p>
     * For example <code>foo_bar</code> becomes <code>FooBar</code>
     * but <code>foo_barBar</code> becomes <code>FooBarbar</code>.
     * </p>
     *
     * @param data string to remove underscores from.
     * @return String
     * @deprecated Use the org.apache.commons.util.StringUtils class
     * instead.  Using its firstLetterCaps() method in conjunction
     * with a StringTokenizer will achieve the same result.
     */
    static public String removeUnderScores (String data)
    {
        String temp = null;
        StringBuffer out = new StringBuffer();
        temp = data;

        StringTokenizer st = new StringTokenizer(temp, "_");

        while (st.hasMoreTokens())
        {
            String element = (String) st.nextElement();
            out.append ( firstLetterCaps(element));
        }

        return out.toString();
    }

    /**
     * <p>
     *  'Camels Hump' replacement of underscores.
     * </p>
     *
     * <p>
     * Remove underscores from a string but leave the capitalization of the
     * other letters unchanged.
     * </p>
     *
     * <p>
     * For example <code>foo_barBar</code> becomes <code>FooBarBar</code>.
     * </p>
     *
     * @param data string to hump
     * @return String
     */
    static public String removeAndHump (String data)
    {
        return removeAndHump(data,"_");
    }

    /**
     * <p>
     * 'Camels Hump' replacement.
     * </p>
     *
     * <p>
     * Remove one string from another string but leave the capitalization of the
     * other letters unchanged.
     * </p>
     *
     * <p>
     * For example, removing "_" from <code>foo_barBar</code> becomes <code>FooBarBar</code>.
     * </p>
     *
     * @param data string to hump
     * @param replaceThis string to be replaced
     * @return String
     */
    static public String removeAndHump (String data,String replaceThis)
    {
        String temp = null;
        StringBuffer out = new StringBuffer();
        temp = data;

        StringTokenizer st = new StringTokenizer(temp, replaceThis);

        while (st.hasMoreTokens())
        {
            String element = (String) st.nextElement();
            out.append ( capitalizeFirstLetter(element));
        }//while

        return out.toString();
    }

    /**
     * <p>
     *  Makes the first letter caps and the rest lowercase.
     * </p>
     *
     * <p>
     *  For example <code>fooBar</code> becomes <code>Foobar</code>.
     * </p>
     *
     * @param data capitalize this
     * @return String
     */
    static public String firstLetterCaps ( String data )
    {
        String firstLetter = data.substring(0,1).toUpperCase();
        String restLetters = data.substring(1).toLowerCase();
        return firstLetter + restLetters;
    }

    /**
     * <p>
     * Capitalize the first letter but leave the rest as they are.
     * </p>
     *
     * <p>
     *  For example <code>fooBar</code> becomes <code>FooBar</code>.
     * </p>
     *
     * @param data capitalize this
     * @return String
     */
    static public String capitalizeFirstLetter ( String data )
    {
        String firstLetter = data.substring(0,1).toUpperCase();
        String restLetters = data.substring(1);
        return firstLetter + restLetters;
    }

    /**
     * Create a string array from a string separated by delim
     *
     * @param line the line to split
     * @param delim the delimter to split by
     * @return a string array of the split fields
     */
    public static String [] split(String line, String delim)
    {
        List list = new ArrayList();
        StringTokenizer t = new StringTokenizer(line, delim);
        while (t.hasMoreTokens())
        {
            list.add(t.nextToken());
        }
        return (String []) list.toArray(new String[list.size()]);
    }

    /**
     * Chop i characters off the end of a string.
     * This method assumes that any EOL characters in String s
     * and the platform EOL will be the same.
     * A 2 character EOL will count as 1 character.
     *
     * @param s String to chop.
     * @param i Number of characters to chop.
     * @return String with processed answer.
     */
    public static String chop(String s, int i)
    {
        return chop(s, i, EOL);
    }

    /**
     * Chop i characters off the end of a string.
     * A 2 character EOL will count as 1 character.
     *
     * @param s String to chop.
     * @param i Number of characters to chop.
     * @param eol A String representing the EOL (end of line).
     * @return String with processed answer.
     */
    public static String chop(String s, int i, String eol)
    {
        if ( i == 0 || s == null || eol == null )
        {
           return s;
        }

        int length = s.length();

        /*
         * if it is a 2 char EOL and the string ends with
         * it, nip it off.  The EOL in this case is treated like 1 character
         */
        if ( eol.length() == 2 && s.endsWith(eol ))
        {
            length -= 2;
            i -= 1;
        }

        if ( i > 0)
        {
            length -= i;
        }

        if ( length < 0)
        {
            length = 0;
        }

        return s.substring( 0, length);
    }

    /**
     * @param argStr
     * @param vars
     * @return Substituted String.
     */
    public static StringBuffer stringSubstitution( String argStr,
                                                   Hashtable vars )
    {
        return stringSubstitution( argStr, (Map) vars );
    }

    /**
     * Perform a series of substitutions. The substitions
     * are performed by replacing $variable in the target
     * string with the value of provided by the key "variable"
     * in the provided hashtable.
     *
     * @param argStr target string
     * @param vars name/value pairs used for substitution
     * @return String target string with replacements.
     */
    public static StringBuffer stringSubstitution(String argStr,
            Map vars)
    {
        StringBuffer argBuf = new StringBuffer();

        for (int cIdx = 0 ; cIdx < argStr.length();)
        {
            char ch = argStr.charAt(cIdx);

            switch (ch)
            {
                case '$':
                    StringBuffer nameBuf = new StringBuffer();
                    for (++cIdx ; cIdx < argStr.length(); ++cIdx)
                    {
                        ch = argStr.charAt(cIdx);
                        if (ch == '_' || Character.isLetterOrDigit(ch))
                            nameBuf.append(ch);
                        else
                            break;
                    }

                    if (nameBuf.length() > 0)
                    {
                        String value =
                                (String) vars.get(nameBuf.toString());

                        if (value != null)
                        {
                            argBuf.append(value);
                        }
                    }
                    break;

                default:
                    argBuf.append(ch);
                    ++cIdx;
                    break;
            }
        }

        return argBuf;
    }

    /**
     * Read the contents of a file and place them in
     * a string object.
     *
     * @param file path to file.
     * @return String contents of the file.
     */
    public static String fileContentsToString(String file)
    {
        String contents = "";

        File f = null;
        try
        {
            f = new File(file);

            if (f.exists())
            {
                FileReader fr = null;
                try
                {
                    fr = new FileReader(f);
                    char[] template = new char[(int) f.length()];
                    fr.read(template);
                    contents = new String(template);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    if (fr != null)
                    {
                        fr.close();
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return contents;
    }

    /**
     * Remove/collapse multiple newline characters.
     *
     * @param argStr string to collapse newlines in.
     * @return String
     */
    public static String collapseNewlines(String argStr)
    {
        char last = argStr.charAt(0);
        StringBuffer argBuf = new StringBuffer();

        for (int cIdx = 0 ; cIdx < argStr.length(); cIdx++)
        {
            char ch = argStr.charAt(cIdx);
            if (ch != '\n' || last != '\n')
            {
                argBuf.append(ch);
                last = ch;
            }
        }

        return argBuf.toString();
    }

    /**
     * Remove/collapse multiple spaces.
     *
     * @param argStr string to remove multiple spaces from.
     * @return String
     */
    public static String collapseSpaces(String argStr)
    {
        char last = argStr.charAt(0);
        StringBuffer argBuf = new StringBuffer();

        for (int cIdx = 0 ; cIdx < argStr.length(); cIdx++)
        {
            char ch = argStr.charAt(cIdx);
            if (ch != ' ' || last != ' ')
            {
                argBuf.append(ch);
                last = ch;
            }
        }

        return argBuf.toString();
    }

    /**
      * Replaces all instances of oldString with newString in line.
      * Taken from the Jive forum package.
      *
      * @param line original string.
      * @param oldString string in line to replace.
      * @param newString replace oldString with this.
      * @return String string with replacements.
      */
    public static final String sub(String line, String oldString,
            String newString)
    {
        int i = 0;
        if ((i = line.indexOf(oldString, i)) >= 0)
        {
            char [] line2 = line.toCharArray();
            char [] newString2 = newString.toCharArray();
            int oLength = oldString.length();
            StringBuffer buf = new StringBuffer(line2.length);
            buf.append(line2, 0, i).append(newString2);
            i += oLength;
            int j = i;
            while ((i = line.indexOf(oldString, i)) > 0)
            {
                buf.append(line2, j, i - j).append(newString2);
                i += oLength;
                j = i;
            }
            buf.append(line2, j, line2.length - j);
            return buf.toString();
        }
        return line;
    }

    /**
     * Returns the output of printStackTrace as a String.
     *
     * @param e A Throwable.
     * @return A String.
     */
    public static final String stackTrace(Throwable e)
    {
        String foo = null;
        try
        {
            // And show the Error Screen.
            ByteArrayOutputStream ostr = new ByteArrayOutputStream();
            e.printStackTrace( new PrintWriter(ostr,true) );
            foo = ostr.toString();
        }
        catch (Exception f)
        {
            // Do nothing.
        }
        return foo;
    }

    /**
     * Return a context-relative path, beginning with a "/", that represents
     * the canonical version of the specified path after ".." and "." elements
     * are resolved out.  If the specified path attempts to go outside the
     * boundaries of the current context (i.e. too many ".." path elements
     * are present), return <code>null</code> instead.
     *
     * @param path Path to be normalized
     * @return String normalized path
     */
    public static final String normalizePath(String path)
    {
        // Normalize the slashes and add leading slash if necessary
        String normalized = path;
        if (normalized.indexOf('\\') >= 0)
        {
            normalized = normalized.replace('\\', '/');
        }

        if (!normalized.startsWith("/"))
        {
            normalized = "/" + normalized;
        }

        // Resolve occurrences of "//" in the normalized path
        while (true)
        {
            int index = normalized.indexOf("//");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
            normalized.substring(index + 1);
        }

        // Resolve occurrences of "%20" in the normalized path
        while (true)
        {
            int index = normalized.indexOf("%20");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) + " " +
            normalized.substring(index + 3);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true)
        {
            int index = normalized.indexOf("/./");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
            normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true)
        {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                return (null);  // Trying to go outside our context
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) +
            normalized.substring(index + 3);
        }

        // Return the normalized path that we have completed
        return (normalized);
    }

    /**
     * If state is true then return the trueString, else
     * return the falseString.
     *
     * @param state
     * @param trueString
     * @param falseString
     * @return Selected result.
     */
    public String select(boolean state, String trueString, String falseString)
    {
        if (state)
        {
            return trueString;
        }
        else
        {
            return falseString;
        }
    }

    /**
     * Check to see if all the string objects passed
     * in are empty.
     *
     * @param list A list of {@link java.lang.String} objects.
     * @return     Whether all strings are empty.
     */
    public boolean allEmpty(List list)
    {
        int size = list.size();

        for (int i = 0; i < size; i++)
        {
            if (list.get(i) != null && list.get(i).toString().length() > 0)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Trim all strings in a List.  Changes the strings in the existing list.
     * @param list
     * @return List of trimmed strings.
     * @since 1.5
     */
    public static List trimStrings(List list)
    {
        if (list == null)
            return null;

        int sz = list.size();
        for (int i = 0; i < sz; i++)
            list.set(i,nullTrim((String) list.get(i)));
        return list;
    }

    /**
     * Trim the string, but pass a null through.
     * @param s
     * @return List of trimmed Strings.
     * @since 1.5
     */
    public static String nullTrim(String s)
    {
        if (s == null)
        {
            return null;
        }
        else
        {
            return s.trim();
        }
    }
}
