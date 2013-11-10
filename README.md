IntelliJ IDEA Postfix Completion plugin
---------------------------------------

The basic idea is to prevent caret jumps backwards while typing Java code,
let you start code with the expression and finish with statement.

#### Download

This plugins is currently under development and do not available as a deployment binary.

#### Features

Currently available templates:

* `.if` – checks boolean expression to be true `if (expr)`
* `.else` – checks boolean expression to be false `if (!expr)`
* `.var` – initialize new variable with expression `T x = expr;`
* `.null` – checks nullable expression to be null `if (expr == null)`
* `.notnull` – checks expression to be non-null `if (expr != null)`

Upcoming templates:

* `.arg` – helps surround argument with invocation `method(expr)`
* `.cast` – surrounds expression with cast `(SomeType) expr`
* `.for` – iterates over collection `for (T item : collection)`
* `.fori` – surrounds with loop `for (int i = 0; i < expr.length; i++)`
* `.forr` – reverse loop `for (int i = expr.length; i >= 0; i--)`
* `.not` – negates value of inner boolean expression `!expr`
* `.field` – introduces field for expression `_field = expr;`
* `.new` – produces instantiation expression for type `new T()`
* `.par` – surrounds outer expression with parentheses `(expr)`
* `.return` – returns value from method/property `return expr;`
* `.switch` – produces switch over integral/string type `switch (expr)`
* `.throw` – throws value of Exception type `throw expr;`
* `.while` – uses expression as loop condition `while (expr)`

Possible templates:

* `.assert` - creates assertion statement from expression `assert expr`
* `.sync` – surrounds expression with statement `synchronized (expr)`
* `.try` – surrounds resource expression with `try (T x = resource)`

#### Feedback

Feel free to post any issues or feature requests here on github or
contact me directly: *alexander.shvedov[at]jetbrains.com*