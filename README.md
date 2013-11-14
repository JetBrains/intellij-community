IntelliJ IDEA Postfix Completion plugin
---------------------------------------

The basic idea is to prevent caret jumps backward while typing code,
let you start with the some expression, explore some APIs, think about
what you are going to do and after finish with statement of some type.

![options](/content/example.png)

#### Download

Plugin currently is under development.
Pre-release versions are available for download in [IntelliJ IDEA plugin repository](http://plugins.jetbrains.com/plugin/7342).
To install into IDEA simply go *Preferences* – *Plugins* – *Browse repositories...* and search for *"postfix"*.

#### Version

Currently plugin tested and works well with IDEA 13 CE (since first preview build v130.754),
but you can easily build it against IDEA 12 SDK. When main development phase will be finished,
I'll investigate ability to work in IDEA 12 and lower the required version if possible.

#### Features

Available templates:

* `.if` – checks boolean expression to be true `if (expr)`
* `.else` – checks boolean expression to be false `if (!expr)`
* `.var` – initialize new variable with expression `T x = expr;`
* `.null` – checks nullable expression to be null `if (expr == null)`
* `.notnull` – checks expression to be non-null `if (expr != null)`
* `.not` – negates value of inner boolean expression `!expr`
* `.for` – iterates over collection `for (T item : collection)`
* `.while` – uses expression as loop condition `while (expr)`

Upcoming templates:

* `.arg` – helps surround argument with invocation `method(expr)`
* `.cast` – surrounds expression with cast `(SomeType) expr`
* `.fori` – surrounds with loop `for (int i = 0; i < expr.length; i++)`
* `.forr` – reverse loop `for (int i = expr.length; i >= 0; i--)`
* `.field` – introduces field for expression `_field = expr;`
* `.new` – produces instantiation expression for type `new T()`
* `.par` – surrounds outer expression with parentheses `(expr)`
* `.return` – returns value from method/property `return expr;`
* `.switch` – produces switch over integral/string type `switch (expr)`
* `.throw` – throws value of Exception type `throw expr;`

Possible templates:

* `.assert` - creates assertion statement from expression `assert expr`
* `.sync` – surrounds expression with statement `synchronized (expr)`
* `.try` – surrounds resource expression with `try (T x = resource)`

Future work:

* Disable statement-based providers in debugger evaluate window
* Settings page to disable/enable templates
* Control braces insertion for statements (use code style settings?)
* Support non-Java languages, of course :)

#### Feedback

Feel free to post any issues or feature requests here on github or
contact me directly: *alexander.shvedov[at]jetbrains.com*