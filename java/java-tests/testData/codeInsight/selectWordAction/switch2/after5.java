public class TestSwitchSelect
{
    public static void main(String[] args)
    {
        switch (type) {
         case 1:
            // Selectable
            break;
      
         case 2:
<selection>            // Not selectable<caret>
            break;
</selection>         default:
            // Gets selected along with case 2
            break;
      } 
    }
} 