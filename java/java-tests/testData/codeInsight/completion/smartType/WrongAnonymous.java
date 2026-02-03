import java.io.File;

class Intermediate {

    {
        new File(".").listFiles(new FileFilter<caret>
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                    }
                });


    }

}
