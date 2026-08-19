```java
class Malformed
```

---

 ```java
public void malformed() { 
[@replace: missing 'replacement' attribute]
[@highlight: unsupported attribute: 'hello']
[Markup tag or attribute expected]
[@replace: missing 'replacement' attribute]
[@link: missing 'target' attribute]
[@link: unknown type 'none'; only 'link' and 'linkplain' are supported]
[@replace: malformed regular expression: Dangling meta character '?' near index 0
???
^]
  System.out.println("hello"); 
[@replace: malformed regular expression replacement '$1': No group 1]
  System.out.println("hello"); 
}
```