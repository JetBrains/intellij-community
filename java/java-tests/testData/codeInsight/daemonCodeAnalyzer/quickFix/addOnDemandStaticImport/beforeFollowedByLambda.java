// "Add on-demand static import for 'test.Bar'" "false"
package test;

class Bar {
    {
        SomeLambda l =
                Ba<caret>r.param -> ();
    }
}

