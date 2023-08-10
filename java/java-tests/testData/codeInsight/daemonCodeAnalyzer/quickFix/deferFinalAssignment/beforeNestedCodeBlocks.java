// "Defer assignment to 'i' using temp variable" "true-preview"
class a {
    {
        final int i;
        if (true) i = 0;
        if (true) <caret>i = 0;
        {if (false);}
    }
}