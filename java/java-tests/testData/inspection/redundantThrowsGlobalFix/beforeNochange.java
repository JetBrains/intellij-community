// "FileNotFoundException" "true"
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

class Main {
  public void f() throws <caret>FileNotFoundException {}

  {
    try (InputStream is = new FileInputStream("")) {
      f();
    } catch (FileNotFoundException e) {
    } catch (Exception e) {
    }

    try (InputStream is = new FileInputStream("")) {
      f();
    } catch (FileNotFoundException | IllegalArgumentException e) {
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

class B {
  {
    try (InputStream is = new FileInputStream("")) {
      new Main().f();
    }
    catch (FileNotFoundException e) {
    }
    catch (Exception e) {
    }

    try (InputStream is = new FileInputStream("")) {
      new Main().f();
    } catch (FileNotFoundException | IllegalArgumentException e) {
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}