/* String.java -- immutable character sequences; the object of string literals
   Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2005
   Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */


import gnu.java.lang.CharData;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Strings represent an immutable set of characters.  All String literals
 * are instances of this class, and two string literals with the same contents
 * refer to the same String object.
 *
 * <p>This class also includes a number of methods for manipulating the
 * contents of strings (of course, creating a new object if there are any
 * changes, as String is immutable). Case mapping relies on Unicode 3.0.0
 * standards, where some character sequences have a different number of
 * characters in the uppercase version than the lower case.
 *
 * <p>Strings are special, in that they are the only object with an overloaded
 * operator. When you use '+' with at least one String argument, both
 * arguments have String conversion performed on them, and another String (not
 * guaranteed to be unique) results.
 *
 * <p>String is special-cased when doing data serialization - rather than
 * listing the fields of this class, a String object is converted to a string
 * literal in the object stream.
 *
 * @author Paul N. Fisher
 * @author Eric Blake (ebb9@email.byu.edu)
 * @author Per Bothner (bothner@cygnus.com)
 * @since 1.0
 * @status updated to 1.4; but could use better data sharing via offset field
 */

public final class StressClass
    implements java.io.Serializable, Comparable<String>, CharSequence
{
  // WARNING: String is a CORE class in the bootstrap cycle. See the comments
  // in vm/reference/java/lang/Runtime for implications of this fact.

  /**
   * This is probably not necessary because this class is special cased already
   * but it will avoid showing up as a discrepancy when comparing SUIDs.
   */
  private static final long serialVersionUID = -6849794470754667710L;

  /**
   * Stores unicode multi-character uppercase expansion table.
   * @see #toUpperCase(char)
   * @see CharData#UPPER_EXPAND
   */
  private static final char[] upperExpand
	= zeroBasedStringValue(CharData.UPPER_EXPAND);

  /**
   * Stores unicode multi-character uppercase special casing table.
   * @see #upperCaseExpansion(char)
   * @see CharData#UPPER_SPECIAL
   */
  private static final char[] upperSpecial
	  = zeroBasedStringValue(CharData.UPPER_SPECIAL);
  
  /**
   * Characters which make up the String.
   * Package access is granted for use by StringBuffer.
   */
  final char[] value;

  /**
   * Holds the number of characters in value.  This number is generally
   * the same as value.length, but can be smaller because substrings and
   * StringBuffers can share arrays. Package visible for use by trusted code.
   */
  final int count;

  /**
   * Caches the result of hashCode().  If this value is zero, the hashcode
   * is considered uncached (even if 0 is the correct hash value).
   */
  private int cachedHashCode;

  /**
   * Holds the starting position for characters in value[].  Since
   * substring()'s are common, the use of offset allows the operation
   * to perform in O(1). Package access is granted for use by StringBuffer.
   */
  final int offset;

  /**
   * An implementation for {@link CASE_INSENSITIVE_ORDER}.
   * This must be {@link Serializable}. The class name is dictated by
   * compatibility with Sun's JDK.
   */
  private static final class CaseInsensitiveComparator
    implements Comparator, Serializable
  {
    /**
     * Compatible with JDK 1.2.
     */
    private static final long serialVersionUID = 8575799808933029326L;

    /**
     * The default private constructor generates unnecessary overhead.
     */
    CaseInsensitiveComparator() {}

    /**
     * Compares to Strings, using
     * <code>String.compareToIgnoreCase(String)</code>.
     *
     * @param o1 the first string
     * @param o2 the second string
     * @return &lt; 0, 0, or &gt; 0 depending on the case-insensitive
     *         comparison of the two strings.
     * @throws NullPointerException if either argument is null
     * @throws ClassCastException if either argument is not a String
     * @see #compareToIgnoreCase(String)
     */
    public int compare(Object o1, Object o2)
    {
      return ((String) o1).compareToIgnoreCase((String) o2);
    }
  } // class CaseInsensitiveComparator

  /**
   * A Comparator that uses <code>String.compareToIgnoreCase(String)</code>.
   * This comparator is {@link Serializable}. Note that it ignores Locale,
   * for that, you want a Collator.
   *
   * @see Collator#compare(String, String)
   * @since 1.2
   */
  public static final Comparator CASE_INSENSITIVE_ORDER
    = new CaseInsensitiveComparator();

  /**
   * Creates an empty String (length 0). Unless you really need a new object,
   * consider using <code>""</code> instead.
   */
  public String()
  {
    value = "".value;
    offset = 0;
    count = 0;
  }

  /**
   * Copies the contents of a String to a new String. Since Strings are
   * immutable, only a shallow copy is performed.
   *
   * @param str String to copy
   * @throws NullPointerException if value is null
   */
  public String(String str)
  {
    value = str.value;
    offset = str.offset;
    count = str.count;
    cachedHashCode = str.cachedHashCode;
  }

  /**
   * Creates a new String using the character sequence of the char array.
   * Subsequent changes to data do not affect the String.
   *
   * @param data char array to copy
   * @throws NullPointerException if data is null
   */
  public String(char[] data)
  {
    this(data, 0, data.length, false);
  }

  /**
   * Creates a new String using the character sequence of a subarray of
   * characters. The string starts at offset, and copies count chars.
   * Subsequent changes to data do not affect the String.
   *
   * @param data char array to copy
   * @param offset position (base 0) to start copying out of data
   * @param count the number of characters from data to copy
   * @throws NullPointerException if data is null
   * @throws IndexOutOfBoundsException if (offset &lt; 0 || count &lt; 0
   *         || offset + count &gt; data.length)
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   */
  public String(char[] data, int offset, int count)
  {
    this(data, offset, count, false);
  }

  /**
   * Creates a new String using an 8-bit array of integer values, starting at
   * an offset, and copying up to the count. Each character c, using
   * corresponding byte b, is created in the new String as if by performing:
   *
   * <pre>
   * c = (char) (((hibyte &amp; 0xff) &lt;&lt; 8) | (b &amp; 0xff))
   * </pre>
   *
   * @param ascii array of integer values
   * @param hibyte top byte of each Unicode character
   * @param offset position (base 0) to start copying out of ascii
   * @param count the number of characters from ascii to copy
   * @throws NullPointerException if ascii is null
   * @throws IndexOutOfBoundsException if (offset &lt; 0 || count &lt; 0
   *         || offset + count &gt; ascii.length)
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   * @see #String(byte[])
   * @see #String(byte[], String)
   * @see #String(byte[], int, int)
   * @see #String(byte[], int, int, String)
   * @deprecated use {@link #String(byte[], int, int, String)} to perform
   *             correct encoding
   */
  public String(byte[] ascii, int hibyte, int offset, int count)
  {
    if (offset < 0 || count < 0 || offset + count > ascii.length)
      throw new StringIndexOutOfBoundsException();
    value = new char[count];
    this.offset = 0;
    this.count = count;
    hibyte <<= 8;
    offset += count;
    while (--count >= 0)
      value[count] = (char) (hibyte | (ascii[--offset] & 0xff));
  }

  /**
   * Creates a new String using an 8-bit array of integer values. Each
   * character c, using corresponding byte b, is created in the new String
   * as if by performing:
   *
   * <pre>
   * c = (char) (((hibyte &amp; 0xff) &lt;&lt; 8) | (b &amp; 0xff))
   * </pre>
   *
   * @param ascii array of integer values
   * @param hibyte top byte of each Unicode character
   * @throws NullPointerException if ascii is null
   * @see #String(byte[])
   * @see #String(byte[], String)
   * @see #String(byte[], int, int)
   * @see #String(byte[], int, int, String)
   * @see #String(byte[], int, int, int)
   * @deprecated use {@link #String(byte[], String)} to perform
   *             correct encoding
   */
  public String(byte[] ascii, int hibyte)
  {
    this(ascii, hibyte, 0, ascii.length);
  }

  /**
   * Creates a new String using the portion of the byte array starting at the
   * offset and ending at offset + count. Uses the specified encoding type
   * to decode the byte array, so the resulting string may be longer or
   * shorter than the byte array. For more decoding control, use
   * {@link java.nio.charset.CharsetDecoder}, and for valid character sets,
   * see {@link java.nio.charset.Charset}. The behavior is not specified if
   * the decoder encounters invalid characters; this implementation throws
   * an Error.
   *
   * @param data byte array to copy
   * @param offset the offset to start at
   * @param count the number of bytes in the array to use
   * @param encoding the name of the encoding to use
   * @throws NullPointerException if data or encoding is null
   * @throws IndexOutOfBoundsException if offset or count is incorrect
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   * @throws UnsupportedEncodingException if encoding is not found
   * @throws Error if the decoding fails
   * @since 1.1
   */
  public String(byte[] data, int offset, int count, String encoding)
    throws UnsupportedEncodingException
  {
    if (offset < 0 || count < 0 || offset + count > data.length)
      throw new StringIndexOutOfBoundsException();
    try 
      {
        CharsetDecoder csd = Charset.forName(encoding).newDecoder();
	csd.onMalformedInput(CodingErrorAction.REPLACE);
	csd.onUnmappableCharacter(CodingErrorAction.REPLACE);
	CharBuffer cbuf = csd.decode(ByteBuffer.wrap(data, offset, count));
 	if(cbuf.hasArray())
 	  {
 	    value = cbuf.array();
	    this.offset = cbuf.position();
	    this.count = cbuf.remaining();
 	  } else {
	    // Doubt this will happen. But just in case.
	    value = new char[cbuf.remaining()];
	    cbuf.get(value);
	    this.offset = 0;
	    this.count = value.length;
	  }
      } catch(CharacterCodingException e){
	  throw new UnsupportedEncodingException("Encoding: "+encoding+
						 " not found.");	  
      } catch(IllegalCharsetNameException e){
	  throw new UnsupportedEncodingException("Encoding: "+encoding+
						 " not found.");
      } catch(UnsupportedCharsetException e){
	  throw new UnsupportedEncodingException("Encoding: "+encoding+
						 " not found.");
      }    
  }

  /**
   * Creates a new String using the byte array. Uses the specified encoding
   * type to decode the byte array, so the resulting string may be longer or
   * shorter than the byte array. For more decoding control, use
   * {@link java.nio.charset.CharsetDecoder}, and for valid character sets,
   * see {@link java.nio.charset.Charset}. The behavior is not specified if
   * the decoder encounters invalid characters; this implementation throws
   * an Error.
   *
   * @param data byte array to copy
   * @param encoding the name of the encoding to use
   * @throws NullPointerException if data or encoding is null
   * @throws UnsupportedEncodingException if encoding is not found
   * @throws Error if the decoding fails
   * @see #String(byte[], int, int, String)
   * @since 1.1
   */
  public String(byte[] data, String encoding)
    throws UnsupportedEncodingException
  {
    this(data, 0, data.length, encoding);
  }

  /**
   * Creates a new String using the portion of the byte array starting at the
   * offset and ending at offset + count. Uses the encoding of the platform's
   * default charset, so the resulting string may be longer or shorter than
   * the byte array. For more decoding control, use
   * {@link java.nio.charset.CharsetDecoder}.  The behavior is not specified
   * if the decoder encounters invalid characters; this implementation throws
   * an Error.
   *
   * @param data byte array to copy
   * @param offset the offset to start at
   * @param count the number of bytes in the array to use
   * @throws NullPointerException if data is null
   * @throws IndexOutOfBoundsException if offset or count is incorrect
   * @throws Error if the decoding fails
   * @see #String(byte[], int, int, String)
   * @since 1.1
   */
  public String(byte[] data, int offset, int count)
  {
    if (offset < 0 || count < 0 || offset + count > data.length)
      throw new StringIndexOutOfBoundsException();
    int o, c;
    char[] v;
    String encoding;
    try 
	{
	  encoding = System.getProperty("file.encoding");
	  CharsetDecoder csd = Charset.forName(encoding).newDecoder();
	  csd.onMalformedInput(CodingErrorAction.REPLACE);
	  csd.onUnmappableCharacter(CodingErrorAction.REPLACE);
	  CharBuffer cbuf = csd.decode(ByteBuffer.wrap(data, offset, count));
	  if(cbuf.hasArray())
	    {
              v = cbuf.array();
	      o = cbuf.position();
	      c = cbuf.remaining();
	    } else {
	      // Doubt this will happen. But just in case.
	      v = new char[cbuf.remaining()];
	      cbuf.get(v);
	      o = 0;
	      c = v.length;
	    }
	} catch(Exception ex){
	    // If anything goes wrong (System property not set,
	    // NIO provider not available, etc)
	    // Default to the 'safe' encoding ISO8859_1
	    v = new char[count];
	    o = 0;
	    c = count;
	    for (int i=0;i<count;i++)
	      v[i] = (char)data[offset+i];
	}
    this.value = v;
    this.offset = o;
    this.count = c;
  }

  /**
   * Creates a new String using the byte array. Uses the encoding of the
   * platform's default charset, so the resulting string may be longer or
   * shorter than the byte array. For more decoding control, use
   * {@link java.nio.charset.CharsetDecoder}.  The behavior is not specified
   * if the decoder encounters invalid characters; this implementation throws
   * an Error.
   *
   * @param data byte array to copy
   * @throws NullPointerException if data is null
   * @throws Error if the decoding fails
   * @see #String(byte[], int, int)
   * @see #String(byte[], int, int, String)
   * @since 1.1
   */
  public String(byte[] data)
  {
    this(data, 0, data.length);
  }

  /**
   * Creates a new String using the character sequence represented by
   * the StringBuffer. Subsequent changes to buf do not affect the String.
   *
   * @param buffer StringBuffer to copy
   * @throws NullPointerException if buffer is null
   */
  public String(StringBuffer buffer)
  {
    synchronized (buffer)
      {
        offset = 0;
        count = buffer.count;
        // Share unless buffer is 3/4 empty.
        if ((count << 2) < buffer.value.length)
          {
            value = new char[count];
            VMSystem.arraycopy(buffer.value, 0, value, 0, count);
          }
        else
          {
            buffer.shared = true;
            value = buffer.value;
          }
      }
  }

  /**
   * Creates a new String using the character sequence represented by
   * the StringBuilder. Subsequent changes to buf do not affect the String.
   *
   * @param buffer StringBuilder to copy
   * @throws NullPointerException if buffer is null
   */
  public String(StringBuilder buffer)
  {
    this(buffer.value, 0, buffer.count);
  }

  /**
   * Special constructor which can share an array when safe to do so.
   *
   * @param data the characters to copy
   * @param offset the location to start from
   * @param count the number of characters to use
   * @param dont_copy true if the array is trusted, and need not be copied
   * @throws NullPointerException if chars is null
   * @throws StringIndexOutOfBoundsException if bounds check fails
   */
  String(char[] data, int offset, int count, boolean dont_copy)
  {
    if (offset < 0 || count < 0 || offset + count > data.length)
      throw new StringIndexOutOfBoundsException();
    if (dont_copy)
      {
        value = data;
        this.offset = offset;
      }
    else
      {
        value = new char[count];
        VMSystem.arraycopy(data, offset, value, 0, count);
        this.offset = 0;
      }
    this.count = count;
  }

  /**
   * Returns the number of characters contained in this String.
   *
   * @return the length of this String
   */
  public int length()
  {
    return count;
  }

  /**
   * Returns the character located at the specified index within this String.
   *
   * @param index position of character to return (base 0)
   * @return character located at position index
   * @throws IndexOutOfBoundsException if index &lt; 0 || index &gt;= length()
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   */
  public char charAt(int index)
  {
    if (index < 0 || index >= count)
      throw new StringIndexOutOfBoundsException(index);
    return value[offset + index];
  }

  /**
   * Copies characters from this String starting at a specified start index,
   * ending at a specified stop index, to a character array starting at
   * a specified destination begin index.
   *
   * @param srcBegin index to begin copying characters from this String
   * @param srcEnd index after the last character to be copied from this String
   * @param dst character array which this String is copied into
   * @param dstBegin index to start writing characters into dst
   * @throws NullPointerException if dst is null
   * @throws IndexOutOfBoundsException if any indices are out of bounds
   *         (while unspecified, source problems cause a
   *         StringIndexOutOfBoundsException, and dst problems cause an
   *         ArrayIndexOutOfBoundsException)
   */
  public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin)
  {
    if (srcBegin < 0 || srcBegin > srcEnd || srcEnd > count)
      throw new StringIndexOutOfBoundsException();
    VMSystem.arraycopy(value, srcBegin + offset,
                     dst, dstBegin, srcEnd - srcBegin);
  }

  /**
   * Copies the low byte of each character from this String starting at a
   * specified start index, ending at a specified stop index, to a byte array
   * starting at a specified destination begin index.
   *
   * @param srcBegin index to being copying characters from this String
   * @param srcEnd index after the last character to be copied from this String
   * @param dst byte array which each low byte of this String is copied into
   * @param dstBegin index to start writing characters into dst
   * @throws NullPointerException if dst is null and copy length is non-zero
   * @throws IndexOutOfBoundsException if any indices are out of bounds
   *         (while unspecified, source problems cause a
   *         StringIndexOutOfBoundsException, and dst problems cause an
   *         ArrayIndexOutOfBoundsException)
   * @see #getBytes()
   * @see #getBytes(String)
   * @deprecated use {@link #getBytes()}, which uses a char to byte encoder
   */
  public void getBytes(int srcBegin, int srcEnd, byte dst[], int dstBegin)
  {
    if (srcBegin < 0 || srcBegin > srcEnd || srcEnd > count)
      throw new StringIndexOutOfBoundsException();
    int i = srcEnd - srcBegin;
    srcBegin += offset;
    while (--i >= 0)
      dst[dstBegin++] = (byte) value[srcBegin++];
  }

  /**
   * Converts the Unicode characters in this String to a byte array. Uses the
   * specified encoding method, so the result may be longer or shorter than
   * the String. For more encoding control, use
   * {@link java.nio.charset.CharsetEncoder}, and for valid character sets,
   * see {@link java.nio.charset.Charset}. Unsupported characters get
   * replaced by an encoding specific byte.
   *
   * @param enc encoding name
   * @return the resulting byte array
   * @throws NullPointerException if enc is null
   * @throws UnsupportedEncodingException if encoding is not supported
   * @since 1.1
   */
  public byte[] getBytes(String enc) throws UnsupportedEncodingException
  {
    try 
      {
	CharsetEncoder cse = Charset.forName(enc).newEncoder();
	cse.onMalformedInput(CodingErrorAction.REPLACE);
	cse.onUnmappableCharacter(CodingErrorAction.REPLACE);
	ByteBuffer bbuf = cse.encode(CharBuffer.wrap(value, offset, count));
	if(bbuf.hasArray())
	  return bbuf.array();

	// Doubt this will happen. But just in case.
	byte[] bytes = new byte[bbuf.remaining()];
	bbuf.get(bytes);
	return bytes;

      } catch(IllegalCharsetNameException e){
	  throw new UnsupportedEncodingException("Encoding: "+enc+
						 " not found.");
      } catch(UnsupportedCharsetException e){
	  throw new UnsupportedEncodingException("Encoding: "+enc+
						 " not found.");
      } catch(CharacterCodingException e){
	  // XXX - Ignore coding exceptions? They shouldn't really happen.
	  return null;
      }	  
  }

  /**
   * Converts the Unicode characters in this String to a byte array. Uses the
   * encoding of the platform's default charset, so the result may be longer
   * or shorter than the String. For more encoding control, use
   * {@link java.nio.charset.CharsetEncoder}. Unsupported characters get
   * replaced by an encoding specific byte.
   *
   * @return the resulting byte array, or null on a problem
   * @since 1.1
   */
  public byte[] getBytes()
  { 
      try 
	  {
	      return getBytes(System.getProperty("file.encoding"));
	  } catch(Exception e) {
	      // XXX - Throw an error here? 
	      // For now, default to the 'safe' encoding.
	      byte[] bytes = new byte[count];
	      for(int i=0;i<count;i++)
		  bytes[i] = (byte)((value[offset+i] <= 0xFF)?
				    value[offset+i]:'?');
	      return bytes;
      }
  }

  /**
   * Predicate which compares anObject to this. This is true only for Strings
   * with the same character sequence.
   *
   * @param anObject the object to compare
   * @return true if anObject is semantically equal to this
   * @see #compareTo(String)
   * @see #equalsIgnoreCase(String)
   */
  public boolean equals(Object anObject)
  {
    if (! (anObject instanceof String))
      return false;
    String str2 = (String) anObject;
    if (count != str2.count)
      return false;
    if (value == str2.value && offset == str2.offset)
      return true;
    int i = count;
    int x = offset;
    int y = str2.offset;
    while (--i >= 0)
      if (value[x++] != str2.value[y++])
        return false;
    return true;
  }

  /**
   * Compares the given StringBuffer to this String. This is true if the
   * StringBuffer has the same content as this String at this moment.
   *
   * @param buffer the StringBuffer to compare to
   * @return true if StringBuffer has the same character sequence
   * @throws NullPointerException if the given StringBuffer is null
   * @since 1.4
   */
  public boolean contentEquals(StringBuffer buffer)
  {
    synchronized (buffer)
      {
        if (count != buffer.count)
          return false;
        if (value == buffer.value)
          return true; // Possible if shared.
        int i = count;
        int x = offset + count;
        while (--i >= 0)
          if (value[--x] != buffer.value[i])
            return false;
        return true;
      }
  }

  /**
   * Compares a String to this String, ignoring case. This does not handle
   * multi-character capitalization exceptions; instead the comparison is
   * made on a character-by-character basis, and is true if:<br><ul>
   * <li><code>c1 == c2</code></li>
   * <li><code>Character.toUpperCase(c1)
   *     == Character.toUpperCase(c2)</code></li>
   * <li><code>Character.toLowerCase(c1)
   *     == Character.toLowerCase(c2)</code></li>
   * </ul>
   *
   * @param anotherString String to compare to this String
   * @return true if anotherString is equal, ignoring case
   * @see #equals(Object)
   * @see Character#toUpperCase(char)
   * @see Character#toLowerCase(char)
   */
  public boolean equalsIgnoreCase(String anotherString)
  {
    if (anotherString == null || count != anotherString.count)
      return false;
    int i = count;
    int x = offset;
    int y = anotherString.offset;
    while (--i >= 0)
      {
        char c1 = value[x++];
        char c2 = anotherString.value[y++];
        // Note that checking c1 != c2 is redundant, but avoids method calls.
        if (c1 != c2
            && Character.toUpperCase(c1) != Character.toUpperCase(c2)
            && Character.toLowerCase(c1) != Character.toLowerCase(c2))
          return false;
      }
    return true;
  }

  /**
   * Compares this String and another String (case sensitive,
   * lexicographically). The result is less than 0 if this string sorts
   * before the other, 0 if they are equal, and greater than 0 otherwise.
   * After any common starting sequence is skipped, the result is
   * <code>this.charAt(k) - anotherString.charAt(k)</code> if both strings
   * have characters remaining, or
   * <code>this.length() - anotherString.length()</code> if one string is
   * a subsequence of the other.
   *
   * @param anotherString the String to compare against
   * @return the comparison
   * @throws NullPointerException if anotherString is null
   */
  public int compareTo(String anotherString)
  {
    int i = Math.min(count, anotherString.count);
    int x = offset;
    int y = anotherString.offset;
    while (--i >= 0)
      {
        int result = value[x++] - anotherString.value[y++];
        if (result != 0)
          return result;
      }
    return count - anotherString.count;
  }

  /**
   * Behaves like <code>compareTo(java.lang.String)</code> unless the Object
   * is not a <code>String</code>.  Then it throws a
   * <code>ClassCastException</code>.
   *
   * @param o the object to compare against
   * @return the comparison
   * @throws NullPointerException if o is null
   * @throws ClassCastException if o is not a <code>String</code>
   * @since 1.2
   */
  public int compareTo(Object o)
  {
    return compareTo((String) o);
  }

  /**
   * Compares this String and another String (case insensitive). This
   * comparison is <em>similar</em> to equalsIgnoreCase, in that it ignores
   * locale and multi-characater capitalization, and compares characters
   * after performing
   * <code>Character.toLowerCase(Character.toUpperCase(c))</code> on each
   * character of the string. This is unsatisfactory for locale-based
   * comparison, in which case you should use {@link java.text.Collator}.
   *
   * @param str the string to compare against
   * @return the comparison
   * @see Collator#compare(String, String)
   * @since 1.2
   */
  public int compareToIgnoreCase(String str)
  {
    int i = Math.min(count, str.count);
    int x = offset;
    int y = str.offset;
    while (--i >= 0)
      {
        int result = Character.toLowerCase(Character.toUpperCase(value[x++]))
          - Character.toLowerCase(Character.toUpperCase(str.value[y++]));
        if (result != 0)
          return result;
      }
    return count - str.count;
  }  

  /**
   * Predicate which determines if this String matches another String
   * starting at a specified offset for each String and continuing
   * for a specified length. Indices out of bounds are harmless, and give
   * a false result.
   *
   * @param toffset index to start comparison at for this String
   * @param other String to compare region to this String
   * @param ooffset index to start comparison at for other
   * @param len number of characters to compare
   * @return true if regions match (case sensitive)
   * @throws NullPointerException if other is null
   */
  public boolean regionMatches(int toffset, String other, int ooffset, int len)
  {
    return regionMatches(false, toffset, other, ooffset, len);
  }

  /**
   * Predicate which determines if this String matches another String
   * starting at a specified offset for each String and continuing
   * for a specified length, optionally ignoring case. Indices out of bounds
   * are harmless, and give a false result. Case comparisons are based on
   * <code>Character.toLowerCase()</code> and
   * <code>Character.toUpperCase()</code>, not on multi-character
   * capitalization expansions.
   *
   * @param ignoreCase true if case should be ignored in comparision
   * @param toffset index to start comparison at for this String
   * @param other String to compare region to this String
   * @param ooffset index to start comparison at for other
   * @param len number of characters to compare
   * @return true if regions match, false otherwise
   * @throws NullPointerException if other is null
   */
  public boolean regionMatches(boolean ignoreCase, int toffset,
                               String other, int ooffset, int len)
  {
    if (toffset < 0 || ooffset < 0 || toffset + len > count
        || ooffset + len > other.count)
      return false;
    toffset += offset;
    ooffset += other.offset;
    while (--len >= 0)
      {
        char c1 = value[toffset++];
        char c2 = other.value[ooffset++];
        // Note that checking c1 != c2 is redundant when ignoreCase is true,
        // but it avoids method calls.
        if (c1 != c2
            && (! ignoreCase
                || (Character.toLowerCase(c1) != Character.toLowerCase(c2)
                    && (Character.toUpperCase(c1)
                        != Character.toUpperCase(c2)))))
          return false;
      }
    return true;
  }

  /**
   * Predicate which determines if this String contains the given prefix,
   * beginning comparison at toffset. The result is false if toffset is
   * negative or greater than this.length(), otherwise it is the same as
   * <code>this.substring(toffset).startsWith(prefix)</code>.
   *
   * @param prefix String to compare
   * @param toffset offset for this String where comparison starts
   * @return true if this String starts with prefix
   * @throws NullPointerException if prefix is null
   * @see #regionMatches(boolean, int, String, int, int)
   */
  public boolean startsWith(String prefix, int toffset)
  {
    return regionMatches(false, toffset, prefix, 0, prefix.count);
  }

  /**
   * Predicate which determines if this String starts with a given prefix.
   * If the prefix is an empty String, true is returned.
   *
   * @param prefix String to compare
   * @return true if this String starts with the prefix
   * @throws NullPointerException if prefix is null
   * @see #startsWith(String, int)
   */
  public boolean startsWith(String prefix)
  {
    return regionMatches(false, 0, prefix, 0, prefix.count);
  }

  /**
   * Predicate which determines if this String ends with a given suffix.
   * If the suffix is an empty String, true is returned.
   *
   * @param suffix String to compare
   * @return true if this String ends with the suffix
   * @throws NullPointerException if suffix is null
   * @see #regionMatches(boolean, int, String, int, int)
   */
  public boolean endsWith(String suffix)
  {
    return regionMatches(false, count - suffix.count, suffix, 0, suffix.count);
  }

  /**
   * Computes the hashcode for this String. This is done with int arithmetic,
   * where ** represents exponentiation, by this formula:<br>
   * <code>s[0]*31**(n-1) + s[1]*31**(n-2) + ... + s[n-1]</code>.
   *
   * @return hashcode value of this String
   */
  public int hashCode()
  {
    if (cachedHashCode != 0)
      return cachedHashCode;

    // Compute the hash code using a local variable to be reentrant.
    int hashCode = 0;
    int limit = count + offset;
    for (int i = offset; i < limit; i++)
      hashCode = hashCode * 31 + value[i];
    return cachedHashCode = hashCode;
  }

  /**
   * Finds the first instance of a character in this String.
   *
   * @param ch character to find
   * @return location (base 0) of the character, or -1 if not found
   */
  public int indexOf(int ch)
  {
    return indexOf(ch, 0);
  }

  /**
   * Finds the first instance of a character in this String, starting at
   * a given index.  If starting index is less than 0, the search
   * starts at the beginning of this String.  If the starting index
   * is greater than the length of this String, -1 is returned.
   *
   * @param ch character to find
   * @param fromIndex index to start the search
   * @return location (base 0) of the character, or -1 if not found
   */
  public int indexOf(int ch, int fromIndex)
  {
    if ((char) ch != ch)
      return -1;
    if (fromIndex < 0)
      fromIndex = 0;
    int i = fromIndex + offset;
    for ( ; fromIndex < count; fromIndex++)
      if (value[i++] == ch)
        return fromIndex;
    return -1;
  }

  /**
   * Finds the last instance of a character in this String.
   *
   * @param ch character to find
   * @return location (base 0) of the character, or -1 if not found
   */
  public int lastIndexOf(int ch)
  {
    return lastIndexOf(ch, count - 1);
  }

  /**
   * Finds the last instance of a character in this String, starting at
   * a given index.  If starting index is greater than the maximum valid
   * index, then the search begins at the end of this String.  If the
   * starting index is less than zero, -1 is returned.
   *
   * @param ch character to find
   * @param fromIndex index to start the search
   * @return location (base 0) of the character, or -1 if not found
   */
  public int lastIndexOf(int ch, int fromIndex)
  {
    if ((char) ch != ch)
      return -1;
    if (fromIndex >= count)
      fromIndex = count - 1;
    int i = fromIndex + offset;
    for ( ; fromIndex >= 0; fromIndex--)
      if (value[i--] == ch)
        return fromIndex;
    return -1;
  }

  /**
   * Finds the first instance of a String in this String.
   *
   * @param str String to find
   * @return location (base 0) of the String, or -1 if not found
   * @throws NullPointerException if str is null
   */
  public int indexOf(String str)
  {
    return indexOf(str, 0);
  }

  /**
   * Finds the first instance of a String in this String, starting at
   * a given index.  If starting index is less than 0, the search
   * starts at the beginning of this String.  If the starting index
   * is greater than the length of this String, -1 is returned.
   *
   * @param str String to find
   * @param fromIndex index to start the search
   * @return location (base 0) of the String, or -1 if not found
   * @throws NullPointerException if str is null
   */
  public int indexOf(String str, int fromIndex)
  {
    if (fromIndex < 0)
      fromIndex = 0;
    int limit = count - str.count;
    for ( ; fromIndex <= limit; fromIndex++)
      if (regionMatches(fromIndex, str, 0, str.count))
        return fromIndex;
    return -1;
  }

  /**
   * Finds the last instance of a String in this String.
   *
   * @param str String to find
   * @return location (base 0) of the String, or -1 if not found
   * @throws NullPointerException if str is null
   */
  public int lastIndexOf(String str)
  {
    return lastIndexOf(str, count - str.count);
  }

  /**
   * Finds the last instance of a String in this String, starting at
   * a given index.  If starting index is greater than the maximum valid
   * index, then the search begins at the end of this String.  If the
   * starting index is less than zero, -1 is returned.
   *
   * @param str String to find
   * @param fromIndex index to start the search
   * @return location (base 0) of the String, or -1 if not found
   * @throws NullPointerException if str is null
   */
  public int lastIndexOf(String str, int fromIndex)
  {
    fromIndex = Math.min(fromIndex, count - str.count);
    for ( ; fromIndex >= 0; fromIndex--)
      if (regionMatches(fromIndex, str, 0, str.count))
        return fromIndex;
    return -1;
  }

  /**
   * Creates a substring of this String, starting at a specified index
   * and ending at the end of this String.
   *
   * @param begin index to start substring (base 0)
   * @return new String which is a substring of this String
   * @throws IndexOutOfBoundsException if begin &lt; 0 || begin &gt; length()
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   */
  public String substring(int begin)
  {
    return substring(begin, count);
  }

  /**
   * Creates a substring of this String, starting at a specified index
   * and ending at one character before a specified index.
   *
   * @param beginIndex index to start substring (inclusive, base 0)
   * @param endIndex index to end at (exclusive)
   * @return new String which is a substring of this String
   * @throws IndexOutOfBoundsException if begin &lt; 0 || end &gt; length()
   *         || begin &gt; end (while unspecified, this is a
   *         StringIndexOutOfBoundsException)
   */
  public String substring(int beginIndex, int endIndex)
  {
    if (beginIndex < 0 || endIndex > count || beginIndex > endIndex)
      throw new StringIndexOutOfBoundsException();
    if (beginIndex == 0 && endIndex == count)
      return this;
    int len = endIndex - beginIndex;
    // Package constructor avoids an array copy.
    return new String(value, beginIndex + offset, len,
                      (len << 2) >= value.length);
  }

  /**
   * Creates a substring of this String, starting at a specified index
   * and ending at one character before a specified index. This behaves like
   * <code>substring(begin, end)</code>.
   *
   * @param begin index to start substring (inclusive, base 0)
   * @param end index to end at (exclusive)
   * @return new String which is a substring of this String
   * @throws IndexOutOfBoundsException if begin &lt; 0 || end &gt; length()
   *         || begin &gt; end
   * @since 1.4
   */
  public CharSequence subSequence(int begin, int end)
  {
    return substring(begin, end);
  }

  /**
   * Concatenates a String to this String. This results in a new string unless
   * one of the two originals is "".
   *
   * @param str String to append to this String
   * @return newly concatenated String
   * @throws NullPointerException if str is null
   */
  public String concat(String str)
  {
    if (str.count == 0)
      return this;
    if (count == 0)
      return str;
    char[] newStr = new char[count + str.count];
    VMSystem.arraycopy(value, offset, newStr, 0, count);
    VMSystem.arraycopy(str.value, str.offset, newStr, count, str.count);
    // Package constructor avoids an array copy.
    return new String(newStr, 0, newStr.length, true);
  }

  /**
   * Replaces every instance of a character in this String with a new
   * character. If no replacements occur, this is returned.
   *
   * @param oldChar the old character to replace
   * @param newChar the new character
   * @return new String with all instances of oldChar replaced with newChar
   */
  public String replace(char oldChar, char newChar)
  {
    if (oldChar == newChar)
      return this;
    int i = count;
    int x = offset - 1;
    while (--i >= 0)
      if (value[++x] == oldChar)
        break;
    if (i < 0)
      return this;
    char[] newStr = (char[]) value.clone();
    newStr[x] = newChar;
    while (--i >= 0)
      if (value[++x] == oldChar)
        newStr[x] = newChar;
    // Package constructor avoids an array copy.
    return new String(newStr, offset, count, true);
  }

  /**
   * Test if this String matches a regular expression. This is shorthand for
   * <code>{@link Pattern}.matches(regex, this)</code>.
   *
   * @param regex the pattern to match
   * @return true if the pattern matches
   * @throws NullPointerException if regex is null
   * @throws PatternSyntaxException if regex is invalid
   * @see Pattern#matches(String, CharSequence)
   * @since 1.4
   */
  public boolean matches(String regex)
  {
    return Pattern.matches(regex, this);
  }

  /**
   * Replaces the first substring match of the regular expression with a
   * given replacement. This is shorthand for <code>{@link Pattern}
   *   .compile(regex).matcher(this).replaceFirst(replacement)</code>.
   *
   * @param regex the pattern to match
   * @param replacement the replacement string
   * @return the modified string
   * @throws NullPointerException if regex or replacement is null
   * @throws PatternSyntaxException if regex is invalid
   * @see #replaceAll(String, String)
   * @see Pattern#compile(String)
   * @see Pattern#matcher(CharSequence)
   * @see Matcher#replaceFirst(String)
   * @since 1.4
   */
  public String replaceFirst(String regex, String replacement)
  {
    return Pattern.compile(regex).matcher(this).replaceFirst(replacement);
  }

  /**
   * Replaces all matching substrings of the regular expression with a
   * given replacement. This is shorthand for <code>{@link Pattern}
   *   .compile(regex).matcher(this).replaceAll(replacement)</code>.
   *
   * @param regex the pattern to match
   * @param replacement the replacement string
   * @return the modified string
   * @throws NullPointerException if regex or replacement is null
   * @throws PatternSyntaxException if regex is invalid
   * @see #replaceFirst(String, String)
   * @see Pattern#compile(String)
   * @see Pattern#matcher(CharSequence)
   * @see Matcher#replaceAll(String)
   * @since 1.4
   */
  public String replaceAll(String regex, String replacement)
  {
    return Pattern.compile(regex).matcher(this).replaceAll(replacement);
  }

  /**
   * Split this string around the matches of a regular expression. Each
   * element of the returned array is the largest block of characters not
   * terminated by the regular expression, in the order the matches are found.
   *
   * <p>The limit affects the length of the array. If it is positive, the
   * array will contain at most n elements (n - 1 pattern matches). If
   * negative, the array length is unlimited, but there can be trailing empty
   * entries. if 0, the array length is unlimited, and trailing empty entries
   * are discarded.
   *
   * <p>For example, splitting "boo:and:foo" yields:<br>
   * <table border=0>
   * <th><td>Regex</td> <td>Limit</td> <td>Result</td></th>
   * <tr><td>":"</td>   <td>2</td>  <td>{ "boo", "and:foo" }</td></tr>
   * <tr><td>":"</td>   <td>t</td>  <td>{ "boo", "and", "foo" }</td></tr>
   * <tr><td>":"</td>   <td>-2</td> <td>{ "boo", "and", "foo" }</td></tr>
   * <tr><td>"o"</td>   <td>5</td>  <td>{ "b", "", ":and:f", "", "" }</td></tr>
   * <tr><td>"o"</td>   <td>-2</td> <td>{ "b", "", ":and:f", "", "" }</td></tr>
   * <tr><td>"o"</td>   <td>0</td>  <td>{ "b", "", ":and:f" }</td></tr>
   * </table>
   *
   * <p>This is shorthand for
   * <code>{@link Pattern}.compile(regex).split(this, limit)</code>.
   *
   * @param regex the pattern to match
   * @param limit the limit threshold
   * @return the array of split strings
   * @throws NullPointerException if regex or replacement is null
   * @throws PatternSyntaxException if regex is invalid
   * @see Pattern#compile(String)
   * @see Pattern#split(CharSequence, int)
   * @since 1.4
   */
  public String[] split(String regex, int limit)
  {
    return Pattern.compile(regex).split(this, limit);
  }

  /**
   * Split this string around the matches of a regular expression. Each
   * element of the returned array is the largest block of characters not
   * terminated by the regular expression, in the order the matches are found.
   * The array length is unlimited, and trailing empty entries are discarded,
   * as though calling <code>split(regex, 0)</code>.
   *
   * @param regex the pattern to match
   * @return the array of split strings
   * @throws NullPointerException if regex or replacement is null
   * @throws PatternSyntaxException if regex is invalid
   * @see #split(String, int)
   * @see Pattern#compile(String)
   * @see Pattern#split(CharSequence, int)
   * @since 1.4
   */
  public String[] split(String regex)
  {
    return Pattern.compile(regex).split(this, 0);
  }

  /**
   * Lowercases this String according to a particular locale. This uses
   * Unicode's special case mappings, as applied to the given Locale, so the
   * resulting string may be a different length.
   *
   * @param loc locale to use
   * @return new lowercased String, or this if no characters were lowercased
   * @throws NullPointerException if loc is null
   * @see #toUpperCase(Locale)
   * @since 1.1
   */
  public String toLowerCase(Locale loc)
  {
    // First, see if the current string is already lower case.
    boolean turkish = "tr".equals(loc.getLanguage());
    int i = count;
    int x = offset - 1;
    while (--i >= 0)
      {
        char ch = value[++x];
        if ((turkish && ch == '\u0049')
            || ch != Character.toLowerCase(ch))
          break;
      }
    if (i < 0)
      return this;

    // Now we perform the conversion. Fortunately, there are no multi-character
    // lowercase expansions in Unicode 3.0.0.
    char[] newStr = (char[]) value.clone();
    do
      {
        char ch = value[x];
        // Hardcoded special case.
        newStr[x++] = (turkish && ch == '\u0049') ? '\u0131'
          : Character.toLowerCase(ch);
      }
    while (--i >= 0);
    // Package constructor avoids an array copy.
    return new String(newStr, offset, count, true);
  }

  /**
   * Lowercases this String. This uses Unicode's special case mappings, as
   * applied to the platform's default Locale, so the resulting string may
   * be a different length.
   *
   * @return new lowercased String, or this if no characters were lowercased
   * @see #toLowerCase(Locale)
   * @see #toUpperCase()
   */
  public String toLowerCase()
  {
    return toLowerCase(Locale.getDefault());
  }

  /**
   * Uppercases this String according to a particular locale. This uses
   * Unicode's special case mappings, as applied to the given Locale, so the
   * resulting string may be a different length.
   *
   * @param loc locale to use
   * @return new uppercased String, or this if no characters were uppercased
   * @throws NullPointerException if loc is null
   * @see #toLowerCase(Locale)
   * @since 1.1
   */
  public String toUpperCase(Locale loc)
  {
    // First, see how many characters we have to grow by, as well as if the
    // current string is already upper case.
    boolean turkish = "tr".equals(loc.getLanguage());
    int expand = 0;
    boolean unchanged = true;
    int i = count;
    int x = i + offset;
    while (--i >= 0)
      {
        char ch = value[--x];
        expand += upperCaseExpansion(ch);
        unchanged = (unchanged && expand == 0
                     && ! (turkish && ch == '\u0069')
                     && ch == Character.toUpperCase(ch));
      }
    if (unchanged)
      return this;

    // Now we perform the conversion.
    i = count;
    if (expand == 0)
      {
        char[] newStr = (char[]) value.clone();
        while (--i >= 0)
          {
            char ch = value[x];
            // Hardcoded special case.
            newStr[x++] = (turkish && ch == '\u0069') ? '\u0130'
              : Character.toUpperCase(ch);
          }
        // Package constructor avoids an array copy.
        return new String(newStr, offset, count, true);
      }

    // Expansion is necessary.
    char[] newStr = new char[count + expand];
    int j = 0;
    while (--i >= 0)
      {
        char ch = value[x++];
        // Hardcoded special case.
        if (turkish && ch == '\u0069')
          {
            newStr[j++] = '\u0130';
            continue;
          }
        expand = upperCaseExpansion(ch);
        if (expand > 0)
          {
            int index = upperCaseIndex(ch);
            while (expand-- >= 0)
              newStr[j++] = upperExpand[index++];
          }
        else
          newStr[j++] = Character.toUpperCase(ch);
      }
    // Package constructor avoids an array copy.
    return new String(newStr, 0, newStr.length, true);
  }

  /**
   * Uppercases this String. This uses Unicode's special case mappings, as
   * applied to the platform's default Locale, so the resulting string may
   * be a different length.
   *
   * @return new uppercased String, or this if no characters were uppercased
   * @see #toUpperCase(Locale)
   * @see #toLowerCase()
   */
  public String toUpperCase()
  {
    return toUpperCase(Locale.getDefault());
  }

  /**
   * Trims all characters less than or equal to <code>'\u0020'</code>
   * (<code>' '</code>) from the beginning and end of this String. This
   * includes many, but not all, ASCII control characters, and all
   * {@link Character#isWhitespace(char)}.
   *
   * @return new trimmed String, or this if nothing trimmed
   */
  public String trim()
  {
    int limit = count + offset;
    if (count == 0 || (value[offset] > '\u0020'
                       && value[limit - 1] > '\u0020'))
      return this;
    int begin = offset;
    do
      if (begin == limit)
        return "";
    while (value[begin++] <= '\u0020');
    int end = limit;
    while (value[--end] <= '\u0020');
    return substring(begin - offset - 1, end - offset + 1);
  }

  /**
   * Returns this, as it is already a String!
   *
   * @return this
   */
  public String toString()
  {
    return this;
  }

  /**
   * Copies the contents of this String into a character array. Subsequent
   * changes to the array do not affect the String.
   *
   * @return character array copying the String
   */
  public char[] toCharArray()
  {
    if (count == value.length)
      return (char[]) value.clone();

    char[] copy = new char[count];
    VMSystem.arraycopy(value, offset, copy, 0, count);
    return copy;
  }

  /**
   * Returns a String representation of an Object. This is "null" if the
   * object is null, otherwise it is <code>obj.toString()</code> (which
   * can be null).
   *
   * @param obj the Object
   * @return the string conversion of obj
   */
  public static String valueOf(Object obj)
  {
    return obj == null ? "null" : obj.toString();
  }

  /**
   * Returns a String representation of a character array. Subsequent
   * changes to the array do not affect the String.
   *
   * @param data the character array
   * @return a String containing the same character sequence as data
   * @throws NullPointerException if data is null
   * @see #valueOf(char[], int, int)
   * @see #String(char[])
   */
  public static String valueOf(char[] data)
  {
    return valueOf (data, 0, data.length);
  }

  /**
   * Returns a String representing the character sequence of the char array,
   * starting at the specified offset, and copying chars up to the specified
   * count. Subsequent changes to the array do not affect the String.
   *
   * @param data character array
   * @param offset position (base 0) to start copying out of data
   * @param count the number of characters from data to copy
   * @return String containing the chars from data[offset..offset+count]
   * @throws NullPointerException if data is null
   * @throws IndexOutOfBoundsException if (offset &lt; 0 || count &lt; 0
   *         || offset + count &gt; data.length)
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   * @see #String(char[], int, int)
   */
  public static String valueOf(char[] data, int offset, int count)
  {
    return new String(data, offset, count, false);
  }

  /**
   * Returns a String representing the character sequence of the char array,
   * starting at the specified offset, and copying chars up to the specified
   * count. Subsequent changes to the array do not affect the String.
   *
   * @param data character array
   * @param offset position (base 0) to start copying out of data
   * @param count the number of characters from data to copy
   * @return String containing the chars from data[offset..offset+count]
   * @throws NullPointerException if data is null
   * @throws IndexOutOfBoundsException if (offset &lt; 0 || count &lt; 0
   *         || offset + count &gt; data.length)
   *         (while unspecified, this is a StringIndexOutOfBoundsException)
   * @see #String(char[], int, int)
   */
  public static String copyValueOf(char[] data, int offset, int count)
  {
    return new String(data, offset, count, false);
  }

  /**
   * Returns a String representation of a character array. Subsequent
   * changes to the array do not affect the String.
   *
   * @param data the character array
   * @return a String containing the same character sequence as data
   * @throws NullPointerException if data is null
   * @see #copyValueOf(char[], int, int)
   * @see #String(char[])
   */
  public static String copyValueOf(char[] data)
  {
    return copyValueOf (data, 0, data.length);
  }

  /**
   * Returns a String representing a boolean.
   *
   * @param b the boolean
   * @return "true" if b is true, else "false"
   */
  public static String valueOf(boolean b)
  {
    return b ? "true" : "false";
  }

  /**
   * Returns a String representing a character.
   *
   * @param c the character
   * @return String containing the single character c
   */
  public static String valueOf(char c)
  {
    // Package constructor avoids an array copy.
    return new String(new char[] { c }, 0, 1, true);
  }

  /**
   * Returns a String representing an integer.
   *
   * @param i the integer
   * @return String containing the integer in base 10
   * @see Integer#toString(int)
   */
  public static String valueOf(int i)
  {
    // See Integer to understand why we call the two-arg variant.
    return Integer.toString(i, 10);
  }

  /**
   * Returns a String representing a long.
   *
   * @param l the long
   * @return String containing the long in base 10
   * @see Long#toString(long)
   */
  public static String valueOf(long l)
  {
    return Long.toString(l);
  }

  /**
   * Returns a String representing a float.
   *
   * @param f the float
   * @return String containing the float
   * @see Float#toString(float)
   */
  public static String valueOf(float f)
  {
    return Float.toString(f);
  }

  /**
   * Returns a String representing a double.
   *
   * @param d the double
   * @return String containing the double
   * @see Double#toString(double)
   */
  public static String valueOf(double d)
  {
    return Double.toString(d);
  }

  /**
   * If two Strings are considered equal, by the equals() method, 
   * then intern() will return the same String instance. ie. 
   * if (s1.equals(s2)) then (s1.intern() == s2.intern()). 
   * All string literals and string-valued constant expressions 
   * are already interned.
   *
   * @return the interned String
   */
  public String intern()
  {
    return VMString.intern(this);
  }

  /**
   * Helper function used to detect which characters have a multi-character
   * uppercase expansion. Note that this is only used in locations which
   * track one-to-many capitalization (java.lang.Character does not do this).
   * As of Unicode 3.0.0, the result is limited in the range 0 to 2, as the
   * longest uppercase expansion is three characters (a growth of 2 from the
   * lowercase character).
   *
   * @param ch the char to check
   * @return the number of characters to add when converting to uppercase
   * @see CharData#DIRECTION
   * @see CharData#UPPER_SPECIAL
   * @see #toUpperCase(Locale)
   */
  private static int upperCaseExpansion(char ch)
  {
    return Character.direction[Character.readChar(ch) >> 7] & 3;
  }

  /**
   * Helper function used to locate the offset in upperExpand given a
   * character with a multi-character expansion. The binary search is
   * optimized under the assumption that this method will only be called on
   * characters which exist in upperSpecial.
   *
   * @param ch the char to check
   * @return the index where its expansion begins
   * @see CharData#UPPER_SPECIAL
   * @see CharData#UPPER_EXPAND
   * @see #toUpperCase(Locale)
   */
  private static int upperCaseIndex(char ch)
  {
    // Simple binary search for the correct character.
    int low = 0;
    int hi = upperSpecial.length - 2;
    int mid = ((low + hi) >> 2) << 1;
    char c = upperSpecial[mid];
    while (ch != c)
      {
        if (ch < c)
          hi = mid - 2;
        else
          low = mid + 2;
        mid = ((low + hi) >> 2) << 1;
        c = upperSpecial[mid];
      }
    return upperSpecial[mid + 1];
  }

  /**
   * Returns the value array of the given string if it is zero based or a
   * copy of it that is zero based (stripping offset and making length equal
   * to count). Used for accessing the char[]s of gnu.java.lang.CharData.
   * Package private for use in Character.
   */
  static char[] zeroBasedStringValue(String s)
  {
    char[] value;

    if (s.offset == 0 && s.count == s.value.length)
      value = s.value;
    else
      {
	int count = s.count;
	value = new char[count];
	VMSystem.arraycopy(s.value, s.offset, value, 0, count);
      }

    return value;
  }
}
