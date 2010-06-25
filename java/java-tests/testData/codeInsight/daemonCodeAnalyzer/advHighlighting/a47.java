// switch statement

class a {
    {
        <error descr="Case statement outside switch">case 0:</error>
    }
    {
        <error descr="Case statement outside switch">default:</error>
    }

    {
        switch (0) {
         case 0<error descr="':' expected">;</error>
        }

        switch (0) {
         default<error descr="':' expected">;</error>
        }

        switch (0) {
        ////////////////
        /** 
        */
        <error descr="Statement must be prepended with case label">System.out.println();</error>
        

        default<error descr="':' expected">;</error>
        }

        switch (0) {
          <error descr="Statement must be prepended with case label">break;</error>
        }
    }

}