# Structural Search and Replace

- You must use terminal search (`ls`, `cat` and `grep`) to read directory `.idea`. Do not use IDE search.

- The contents of structural search inspections is located in `.idea/inspectionProfiles/idea_default.xml`

- Here is an example of a Structural Search and Replace inspection:

```xml

<replaceConfiguration name="Use Strings.areSameInstance instead of =="
                      description="Comparing Strings by references usually indicate a mistake, if you really need to do this (for performance reeasons or to implement &quot;Sentinel&quot; pattern), use Strings.areSameInstance method to make the intention explicit and get rid of warning."
                      suppressId="StringEqualitySSR"
                      problemDescriptor="Use !Strings.areSameInstance instead of '!=' if you really need to compare strings by reference"
                      text="$s1$ == $s2$" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default"
                      search_injected="false" reformatAccordingToStyle="false" shortenFQN="true"
                      replacement="com.intellij.openapi.util.text.Strings.areSameInstance($s1$, $s2$)">
  <constraint name="__context__" within="" contains=""/>
  <constraint name="s1" nameOfExprType="java\.lang\.String" within="" contains=""/>
  <constraint name="s2" nameOfExprType="java\.lang\.String" within="" contains=""/>
</replaceConfiguration>
```

This node should be located under `<inspection_tool class="SSBasedInspection"` tag.

- Here is an example of a Structural Search inspection:

```xml

<searchConfiguration name="Raw coroutine scope creation" uuid="e11e9d2f-7cc2-3359-a78a-24f67cbe0850"
                     description="Coroutine scope should be created:&#10;&lt;ul&gt;&#10;&lt;li&gt;by a coroutine builder (&lt;code&gt;launch&lt;/code&gt;, &lt;code&gt;async&lt;/code&gt;, &lt;code&gt;runBlockingCancellable&lt;/code&gt;);&lt;/li&gt;&#10;&lt;li&gt;by a scoping function (&lt;code&gt;withContext&lt;/code&gt;, &lt;code&gt;coroutineScope&lt;/code&gt;, &lt;code&gt;supervisorScope&lt;/code&gt;);&lt;/li&gt;&#10;&lt;li&gt;by injecting it into a service constructor;&lt;/li&gt;&#10;&lt;li&gt;by explicitly creating a child scope (&lt;code&gt;childScope&lt;/code&gt;, &lt;code&gt;namedChildScope&lt;/code&gt;)&lt;/li&gt;&#10;&lt;/ul&gt; "
                     suppressId="RAW_SCOPE_CREATION"
                     problemDescriptor="Raw scope might not be linked to any parent unintentionally (if passed context does not have any &lt;code&gt;Job&lt;/code&gt;, or if passed &lt;code&gt;Job&lt;/code&gt; does not have any parent). Use &lt;code&gt;namedChildScope()&lt;/code&gt; on some existing scope instead. If no parent is actually intended, use &lt;code&gt;GlobalScope.namedScope()&lt;/code&gt;."
                     text="CoroutineScope($args$)" recursive="true" caseInsensitive="true" type="Kotlin" pattern_context="default"
                     search_injected="false">
  <constraint name="__context__" within="" contains=""/>
  <constraint name="args" within="" contains=""/>
</searchConfiguration>
```

This node should be located under `<inspection_tool class="SSBasedInspection"` tag.

- To configure the scope of inspection, you need to make changes similar to this pattern, where class is UUID of the created replace
  configuration.

```xml

<inspection_tool class="e5d3c6f8-12ab-4cc9-8e51-8a8f1bd1c3e2" enabled="true" level="WARNING" enabled_by_default="false">
  <scope name="IDE Testing Framework" level="WARNING" enabled="true"/>
  <scope name="Tests" level="WARNING" enabled="true"/>
  <scope name="test-framework" level="WARNING" enabled="true"/>
</inspection_tool>
```

- You must run `InspectionProfileConsistencyTest` after changing xml files
