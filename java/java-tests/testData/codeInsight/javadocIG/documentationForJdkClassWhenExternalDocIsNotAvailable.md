From package: [`java.lang`](java.lang)
```java
public final class String
implements java.io.Serializable, Comparable<String>, CharSequence
```

---

 The `String` class represents character strings. All
 string literals in Java programs, such as `"abc"`, are
 implemented as instances of this class.

 Strings are constant; their values cannot be changed after they
 are created. String buffers support mutable strings.
 Because String objects are immutable they can be shared. For example:

 
```java
     String str = "abc";  
```


 is equivalent to:

 
```java
     char data[] = {'a', 'b', 'c'};
     String str = new String(data);  
```


 Here are some more examples of how strings can be used:

 
```java
     System.out.println("abc");
     String cde = "cde";
     System.out.println("abc" + cde);
     String c = "abc".substring(2,3);
     String d = cde.substring(1, 2);  
```


 The class `String` includes methods for examining
 individual characters of the sequence, for comparing strings, for
 searching strings, for extracting substrings, and for creating a
 copy of a string with all characters translated to uppercase or to
 lowercase. Case mapping is based on the Unicode Standard version
 specified by the [`Character`](java.lang.Character) class.

 The Java language provides special support for the string
 concatenation operator (&nbsp;+&nbsp;), and for conversion of
 other objects to strings. String concatenation is implemented
 through the `StringBuilder`(or `StringBuffer`)
 class and its `append` method.
 String conversions are implemented through the method
 `toString`, defined by `Object` and
 inherited by all classes in Java. For additional information on
 string concatenation and conversion, see Gosling, Joy, and Steele,
 _The Java Language Specification_.

 Unless otherwise noted, passing a `null` argument to a constructor
 or method in this class will cause a [`NullPointerException`](java.lang.NullPointerException) to be
 thrown.

 A `String` represents a string in the UTF-16 format
 in which _supplementary characters_ are represented by _surrogate
 pairs_ (see the section [Unicode
 Character Representations](Character.html#unicode) in the `Character` class for
 more information).
 Index values refer to `char` code units, so a supplementary
 character uses two positions in a `String`.

 The `String` class provides methods for dealing with
 Unicode code points (i.e., characters), in addition to those for
 dealing with Unicode code units (i.e., `char` values).

 

**Since:**
   JDK1.0  

**See Also:**
[`Object.toString()`](java.lang.Object#toString--),  
[`StringBuffer`](java.lang.StringBuffer),  
[`StringBuilder`](java.lang.StringBuilder),  
java.nio.charset.Charset  

**Author:**
Lee Boynton, Arthur van Hoff, Martin Buchholz, Ulf Zibis