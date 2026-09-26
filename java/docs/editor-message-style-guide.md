# A style guide for Java error, warning and intention messages

(may also be applicable to other languages)

## Why this style guide?

To try to get better quality messages. When all considerations for editor message are in a single place, it will be easier to check
if new editor messages take them into account. We will be able to create more consistent editor message, making them easier to
understand. By using a common style it will also be easier to create new editor messages. The wheel does not need to be
reinvented everytime. This will also help avoiding mistakes that are common when creating editor messages. Hopefully without needing
to think too hard about it.

## Who is the target audience for editor messages?

When creating editor messages, as with creating any text, it is important to keep in mind the target audience.
It seems to me our error messages are most often seen by beginning developers. Experienced developers will usually avoid
seeing the errors and if they do trigger some problem by mistake will recognize the issue and know how to fix it by just
looking at the red highlighting.

Inspection warnings however are more often seen by developers of any level. Old code can suddenly get new warnings because of
new language features in a new version of the Java SDK for example. And every new release of IntelliJ IDEA brings new
inspections with new warnings no-one has ever seen before.

## Previous work

There is a small amount of previous work in the UI Guidelines section
[Writing UI Texts](https://plugins.jetbrains.com/docs/intellij/inspections.html#editor-messages).
It is not much, but does provide the useful umbrella term "Editor Messages". This nicely captures all informational, warning and
error messages that are shown in the editor.

## Guidelines

### 1. Make messages short
An editor message should be short, so you can see quickly what the problem is. Editor messages can appear a lot, it would get boring
very soon if you had to read a lot of unnecessary text every time. We have some advantages over a command line compiler here, because
we often don't have to explain how to fix something; we have quick-fixes for that. And because of our highlighting, the location of
the problem and its context is clear.

To create short editor messages we can take inspiration from newspaper headlines. Newspaper headlines have limited space and need to
capture attention, similar to our situation. They use several tricks that we can copy.

Newspaper headlines commonly:
1. Use no, or very few articles like "the" and "a"
2. Avoid auxiliary verbs like "is" or "has"
3. Use punctuation instead of conjunctions like "because"

### 2. Make messages easy to understand
Many, probably most, developers are not native speakers of the English language. And some may barely be able to understand
English at all. Since we don't offer localization for every language yet, we should take this into account. There are tools that
can help you use only or mostly simple words, for example: https://xkcd.com/simplewriter/
And use mostly only the present tense or simple past tense. This way our messages can hopefully be easier understood by people
with only a basic understanding of English.

Also try to avoid jargon. Sometimes jargon cannot be avoided, but try to use only the most well known among developers.

Suggestions for replacements for some complex words that may be easier to understand.

- `resolve` → `find`
- `invocation` → `call`
- `invoked` → `called`
- `illegal` → `not allowed`
- `reference` → `access`

### 3. Be precise
It should be clear what the message is referring to. In Java code a method, a field and a local variable or parameter can have
the same name. When using referring to an element by its name, it is therefore good to include what kind of member it is. For example
`class 'X'` instead of just `'X'`

When referring to some name or keyword from the code, surround it by single quotes. If possible use identifiers from the code in the
message. Avoid using the words "here", for example `not allowed here`, but name the location e.g. "not allowed in implements list".
Often the same applies to the word "this". Messages should try to be specific, not generic.

Sometimes it's possible to split up a message into more message and increase accuracy.
For example instead of mentioning several cases in a single message: `from enum constructor or instance initializer` have separate
messages for `from enum constructor` and `from instance initializer` (see the example section below).

When mentioning a method name, add "()" after the name, so that it can't be confusing with e.g. a field. For example `'x()' call` or
`method 'x()'`

Use plural words where this is clearer. For example `<x> not allowed in string literals`, makes it clearer that <x> is not allowed in
all string literals, not only in the specific string literal being reported on.

### 4. Make messages consistent
Use existing messages as inspiration for new messages. It is encouraged to use the same style and wording. There is no need to be
original, unless it is clearly better.
Use "not allowed" instead of "cannot be", "should not be" or "illegal"
A common pattern is: `Expected '<x>', found '<y>'` or just `Expected <...>`. It is almost surprising how often it can be used.

### 5. Be polite
This goes without saying, but there's no need to be rude. Respect the user's code, since it might very well be that it is
in fact correct and our warning is a false positive. To avoid say the code is `incorrect`, `wrong` or `useless`

Since developers are not getting arrested yet for writing bad code,
try to avoid the word `illegal` in editor messages. Don't say that something `must be` or `should be`, since we aren't forcing people
to do anything, often `expected` can be used instead.

### 6. Test
When creating a new editor message, it is always a good idea to test it on some large body of code. Quite often it turns out there are
some edge case where the message is incorrect or where the message can be improved.

## Examples
Here are some examples of how editor messages have been improved in the past or could be improved in the future.

old message  →
new and improved message

Making a complex message simpler:

`attempting to assign weaker access privileges ('package-private'); was 'public'` →
`cannot reduce visibility from 'public' to 'package-private'`

Adding quotes around keyword:

`return not allowed before 'super()' call` →
`'return' not allowed before 'super()' call`

No need for article and added hyphen to fix grammar:

`Call to 'super()' must be a top level statement in constructor body` →
`Call to 'super()' must be top-level statement in constructor body`

Clarify message:

`Class is not allowed to extend sealed class from another package` →
`Class 'Mail' from another package not allowed to extend sealed class 'p1.Envelope' in unnamed module`

Replace "must be" with expected:

`Annotation attribute must be of the form 'name=value'` →
`Annotation attribute of the form 'name=value' expected`

More clear, much shorter:

`'EnumWithoutExpectedArguments(int)' in 'EnumWithoutExpectedArguments' cannot be applied to '()'` →
`Expected 1 argument, found 0`

Clarify message and context:

`Duplicate class: 'A'` →
`Duplicate reference to class 'A' in 'extends' list`

Avoid the word "clause", use consistent "not allowed" formulation

`No implements clause allowed for interface` →
`'implements' list not allowed on interface`

"invoked" → "called"

`Static method may be invoked on containing interface class only` →
`Static method may only be called on its containing interface`

Explain why, not just what:

`Generic array creation` →
`Generic array creation not allowed`

"illegal" → "not allowed", remove auxiliary verb, split up message:

`It is illegal to access static member 'FOO' from enum constructor or instance initializer` →
`Accessing enum constant from enum instance initializer not allowed`
`Accessing enum constant from enum instance field initializer not allowed`
`Accessing enum constant from enum constructor not allowed`

Weird message:

`Illegal combination of modifiers: 'public' and 'public'` →
`Repeated 'public' modifier`

Call the police:

`Illegal line end in string literal` →
`Line ends not allowed in string literals`

Sometimes a little longer is more clear:

`C-style record component declaration is not allowed` →
`C-style array declaration not allowed in record component`

"should be" → "only allowed", quotes:

`Package annotations should be in the package-info.java file` →
`Package annotations only allowed in 'package-info.java' files`

"invocation" → "call"

`Recursive constructor invocation` →
`Recursive constructor call`

Use "expected" formulation to avoid auxiliary verb and slightly weird "no no-arg" wording. Added "class":

`There is no no-arg constructor available in 'X'` →
`Expected no-arg constructor in class 'X'`

## Further reading
[Writing Good Compiler Error Messages](https://calebmer.com/2019/07/01/writing-good-compiler-error-messages.html)

But note that this guide does not agree with everything in the article.