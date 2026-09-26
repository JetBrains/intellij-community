From class: [`My`](My)
```java
@Contract(pure = true)⁽ⁱ⁾ 
boolean contains(
    Object o
)
throws IOException
```

**From interface:**
[`java.util.Collection`](java.util.Collection)  

 Returns <tt>true</tt> if this collection contains the specified element.
 More formally, returns <tt>true</tt> if and only if this collection
 contains at least one element <tt>e</tt> such that
 <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.

 

**Overrides:**
[`contains`](java.util.Collection#contains-java.lang.Object-) in interface [`Collection`](java.util.Collection)  
[`contains`](I#contains-java.lang.Object-) in interface [`I`](I)  

**Params:**
`o` &ndash; element whose presence in this collection is to be tested  

**Returns:**
<tt>true</tt> if this collection contains the specified
         element  

**Throws:**
[`NullPointerException`](java.lang.NullPointerException) &ndash; before if the specified element is null and this          collection does not permit null elements          (<a href="#optional-restrictions">optional</a>) after

IOException