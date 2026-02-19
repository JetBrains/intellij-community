public class TestSwitchSelect
{
    public static void main(String[] args)
    {
        switch (type) {
         case 1:
            // Selectable
            break;
      
         case 2:
            // Not selectable<caret>
            break;
         default:
            // Gets selected along with case 2
            break;
      } 
    }
} 