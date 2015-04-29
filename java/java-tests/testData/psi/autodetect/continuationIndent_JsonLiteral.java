//package com.proofpoint.forensics.afrm;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

public class FooTest {

    @Test
    public void foo() throws Exception {
        JsonNode json = test.toJson(
                "[",
                "  {",
                "    \"foo\": { ",
                "      \"bar\": 17,",
                "      \"xyz\": 19 ",
                "    }",
                "  },",
                "  {",
                "    \"foo\": { ",
                "      \"bar\": 17,",
                "      \"xyz\": 19 ",
                "    }",
                "  },",
                "  {",
                "    \"foo\": { ",
                "      \"bar\": 17,",
                "      \"xyz\": 19 ",
                "    }",
                "  },",
                "  {",
                "    \"foo\": { ",
                "      \"bar\": 17,",
                "      \"xyz\": 19 ",
                "    }",
                "  }",
                "]");
    }
}