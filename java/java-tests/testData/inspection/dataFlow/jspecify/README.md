# Sample inputs

Currently, the main purpose of these samples is to act as a source of test cases
for authors of nullness checkers that wish to use JSpecify nullness information.

## Disclaimers

These sample inputs are informative, not normative.

They use annotations whose names and meanings are not finalized. Notably, they
currently use an explicit `@NullnessUnspecified` annotation, even though that
annotation was not part of our 0.1 milestone and will quite possibly not be part
of our 1.0 release. (Still, we haven't yet removed the `@NullnessUnspecified`
usages: First, we're not yet certain whether we'll include that annotation or
not. Second, the annotation provides an easier way to demonstrate rules that can
arise without it but are easier to demonstrate with it. We probably wouldn't
write such samples if we were starting from scratch today, but we wrote them
when we started, and they appear to provide some value on net.)

They have mostly not been code reviewed.

We do not know if this is even the format that we want our sample inputs to be
in.

Whatever our final samples look like, we do **not** expect to present them as
"conformance tests" that require any behavior from tools:

-   JSpecify nullness information may be of use to many kinds of tools, not just
    "nullness checkers." But these samples are written in a way that makes them
    most useful to authors of nullness checkers. Authors of other tools -- those
    that render API descriptions, generate source code, perform refactorings,
    etc. -- are not best served by the samples' focus on
    `jspecify_nullness_mismatch`, etc.

-   The goal of JSpecify is to provide one source of nullness information. Tools
    may use some, all, or none of that information. They may also use
    information from other sources.

    -   Some examples of "information from other sources":

        -   looking at the *implementation* of a method to decide whether it can
            return `null`
        -   looking at non-JSpecify nullness annotations in code
        -   looking at "overlay" or "stub" information about nullness for
            well-known APIs
        -   looking at whether the parameter to a call to `Map.get` is known to
            be present in that map
        -   defining a rule to treat all unannotated type usages the same, when
            the JSpecify rules give some of them "unspecified nullness"

-   Based on the information they have available for any given piece of code,
    tools always have the option to issue a warning / error / other diagnostic
    for that code, and they always have the option not to. (Even for a tool that
    uses *all* JSpecify information and *only* JSpecify information, that
    information is, at its heart, *information* for tools to apply as their
    authors see fit.)

## Syntax

<!-- TODO: Update links to point to the markup-format spec and glossary. -->

A special comment on a given line of a `.java` file provides information about
the following line.

The first three special comments indicate that JSpecify annotations are applied
in ways that are
[unrecognized](http://jspecify.org/spec#recognized-locations-type-use). Tools
are likely to report an error in the case of the first two, somewhat less likely
to report an error in the case of the third (since they might choose to give
their meaning to annotations there), and not *obligated* to do anything for any
of the cases:

-   `jspecify_conflicting_annotations`: for cases like `@Nullable
    @NullnessUnspecified Foo`

-   `jspecify_nullness_intrinsically_not_nullable`: for cases like `@Nullable
    int`

-   `jspecify_unrecognized_location`: for the other cases in which JSpecify does
    not give meaning to an annotation, like `class @Nullable Foo {}`.

The last two comments indicate a nullness violation: an inconsistency between
two annotations, or between annotations and source code. You can think of these
as extending the normal JLS type rules to cover types that have been augmented
with nullness information. (For example, the value of a `return` statement must
be convertible to the method's return type, and the receiver in a method call
should not be `@Nullable`.) Nullness checkers are likely to report an error for
each `jspecify_nullness_mismatch` comment, are likely to make many different
decisions in whether to issues a diagnostic (error, warning, or no diagnostic)
for any particular `jspecify_nullness_not_enough_information` comment, and are
not *obligated* to do anything for any particular comment. (For some background
on that, see the disclaimers above. Also, note that a nullness checker can be
sound even if it does not issue errors for some cases of
`jspecify_nullness_mismatch` or `jspecify_nullness_not_enough_information`!)

-   `jspecify_nullness_mismatch`

-   `jspecify_nullness_not_enough_information`: for nullness violations that
    involve
    [unspecified nullness](https://docs.google.com/document/d/1KQrBxwaVIPIac_6SCf--w-vZBeHkTvtaqPSU_icIccc/edit#bookmark=id.xb9w6p3ilsq3).

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
