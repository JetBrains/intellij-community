# Sample inputs

## Disclaimers

**These sample inputs are an experiment.**

They use annotations whose names and meanings are not finalized.

They have not been code reviewed.

We do not know if this is even the format that we want our sample inputs to be
in.

Whatever our final samples look like, we do **not** expect to present them as
"conformance tests" that require any behavior from tools: The goal of our
project is to provide one source of nullness information. Tools may use some,
all, or none of that information. They may also use information from other
sources. Based on the information they have available for any given piece of
code, tools always have the option to issue a warning / error / other diagnostic
for that code, and they always have the option not to.

The hope is that samples like these may be useful to those who wish to discuss
the spec or implement tools. To that end, we welcome comments on both the format
of these files and their content.

## Syntax

<!-- TODO: Update links to point to the markup-format spec and glossary. -->

A special comment on a given line of a `.java` file provides information about the following line.

The first two special comments indicate that JSpecify annotations are applied in a way that
is [structurally
illegal](https://docs.google.com/document/d/15NND5nBxMkZ-Us6wz3Pfbt4ODIaWaJ6JDs4w6h9kUaY/edit#heading=h.ib00ltjpj1xa):

-   `jspecify_conflicting_annotations`: for cases like `@Nullable
    @NullnessUnspecified Foo`

-   `jspecify_nullness_intrinsically_not_nullable`: for cases like `@Nullable
    int`

The third indicates a nullness violation:  an inconsistency between two
annotations, or between annotations and source code.  You can think of
these as expressing type rules, analogous to the normal JLS type rules.
(For example, the value of a `return` statement must be convertible to the
method's return type, and the receiver in a method call should not be
`@Nullable`.)  All tools are likely (but not obligated!) to report an error
at these locations.

-   `jspecify_nullness_mismatch`

Two further categories are relevant to some JSpecify checkers, but not all.

-   `jspecify_unrecognized_location`: for cases like `class @Nullable Foo {}`,
    in which JSpecify neither forbids annotations nor currently specifies
    meaning for such annotations.  Some checkers may process annotations in
    these locations and give them a meaning.

-   `jspecify_nullness_not_enough_information`: for nullness violations that
    involve [unspecified
    nullness](https://docs.google.com/document/d/1KQrBxwaVIPIac_6SCf--w-vZBeHkTvtaqPSU_icIccc/edit#bookmark=id.xb9w6p3ilsq3)
    (the `@NullnessUnspecified` annotation).  A checker is permitted to
    treat such annotations in any way it wishes, so a checker may issue
    errors or warnings at all, some, or none of these locations.


TODO: Consider additional features:

-   multiline comments
-   other locations for comments
-   multiple findings per line/comment
-   comments that apply to larger ranges -- possibly to syntax elements (like
    statements) rather than lines
-   comments that apply only to a particular part of the line

## Directory structure

See
[JSpecify: test-data format: Directory structure](https://docs.google.com/document/d/1JVH2p61kReO8bW4AKnbkpybPYlUulVmyNrR1WRIEE_k/edit#bookmark=id.2t1r58i5a03s).
TODO(#134): Inline that here if Tagir can sign the CLA and contribute it.

Additionally:

Fully qualified class names must be unique across all directories.

> This permits all files to be compiled in a single tool invocation.

TODO: Consider requiring that all individual-file samples be in the top-level
directory.

Each file must contain a single top-level class. TODO(#133): Consider relaxing
this.

TODO: Consider requiring a file's path to match its package and class:

-   individual-file samples: `Foo.java` for `Foo`

-   full-directory samples: `sampleFoo/Foo.java` for `Foo`,
    `sampleFoo/bar/Foo.java` for `bar.Foo`

-   We may need additional accommodations for JPMS support to demonstrate
    module-level defaulting.

## Restrictions

Files must be UTF-8 encoded.

Files must contain only printable ASCII characters and `\n`.

Files must be compatible with Java 8. TODO(#131): Decide how to label files that
require a higher version so that we can allow them. (But still encourage
sticking to Java 8 except for tests that specifically exercise newer features.)

Files must compile without error using stock javac.

Files must not depend on any classes other than the JSpecify annotations. This
includes the Java platform APIs. Exception: Files may use `java.lang.Object`,
but they still must not use its methods.

> For example, files may use `Object` as a bound, parameter, or return type.

Files should avoid depending on the presence of absence of "smart" checker
features, such as:

-   looking inside the body of a method to determine what parameters it
    dereferences or what it returns

    -   To that end, prefer abstract methods when practical.

-   flow-sensitive typing

We also encourage writing files that demonstrate individual behaviors in
isolation. For example, we encourage writing files to minimize how much they
rely on type inference -- except, of course, for any files explicitly intended
to demonstrate type inference.

## More TODOs

TODO: Consider how to map between samples and related GitHub issues (comments,
filenames?).
