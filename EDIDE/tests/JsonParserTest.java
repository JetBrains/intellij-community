import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import ru.compscicenter.edide.course.Course;

import java.io.*;

/**
 * author: liana
 * data: 7/4/14.
 */
public class JsonParserTest {
  private Course myCourse = null;
  @Before
  public void setUp() throws FileNotFoundException {
    Reader reader = new InputStreamReader(new FileInputStream("testData/course.json"));
    Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    myCourse = gson.fromJson(reader, Course.class);
  }

  @Test
  public void testCourseLevel() {
    assertEquals(myCourse.getName(), "Python для начинающих");
    assertEquals(myCourse.getLessons().size(), 2);
    assertEquals(myCourse.getLessons().get(0).getTaskList().size(), 2);
    assertEquals(myCourse.getLessons().get(1).getTaskList().size(), 1);
  }
}
