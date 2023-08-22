// "Defer assignment to 'i' using temp variable" "true-preview"
class a {
    {
        final int i;
        int i1;
        if (true) i1 = 0;
        if (true) i1 = 0;
        i = i1;
        {if (false);}
    }
}