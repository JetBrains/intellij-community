From class: [`java.util.List<E>`](java.util.List)
```java
@Contract(pure = true) 
public abstract boolean contains(
    Object o
)
```

---

 Returns `true` if this list contains the specified element.
 More formally, returns `true` if and only if this list contains
 at least one element `e` such that
 `(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))`.

 

**Overrides:**
[`contains`](java.util.Collection#contains-java.lang.Object-) in interface [`Collection`](java.util.Collection)  

**Params:**
`o` &ndash; element whose presence in this list is to be tested  

**Returns:**
`true` if this list contains the specified element  

**Throws:**
[`ClassCastException`](java.lang.ClassCastException) &ndash; if the type of the specified element
         is incompatible with this list  ([optional](java.util.Collection))  

[`NullPointerException`](java.lang.NullPointerException) &ndash; if the specified element is null and this
         list does not permit null elements  ([optional](java.util.Collection))