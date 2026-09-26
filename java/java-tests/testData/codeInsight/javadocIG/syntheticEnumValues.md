From class: [`E`](E)
```java
@Contract(value = " -> new", pure = true)⁽ⁱ⁾ 
public static E[] values()
```

---

 Returns an array containing the constants of this enum
 type, in the order they're declared.  This method may be
 used to iterate over the constants as follows:
 <pre>
    for(E c : E.values())
        System.out.println(c);
 </pre>

 

**Returns:**
an array containing the constants of this enum
 type, in the order they're declared