There are trivial and yet breaking changes to the way in which things
are output in Groovy 1.6.x compared to Groovy 1.5.x.  In particular,
strings in lists are no longer embedded in double quotes on output --
this makes things more harmonious with the way Java does things -- and
the groovyc task no longer issues the message "No sources to compile"
when there are no sources to compile.  Sadly, it means some of the
Gant tests have to distinguish which version of Groovy is being used.
If support for Groovy 1.5.x is removed then this code must be reviewed
and the special code removed.

The Gant Ant task tests requires the Ant XML scripts to have access to
the org.codehaus.gant.ant.Gant class.  This means the path to the
class has to be known.  Currently the Ant/Gant build compilation
products are used.  This has "quite interesting" consequences: in
particular, the Eclipse, NetBeans, IntelliJ IDEA and Gradle builds put
the compilation products in their own private places.  Unless the
Ant/Gant build is created as well then these other builds will fail a
number of the org.codehaus.gant.ant.tests.Gant_Test tests.  Moreover,
both the product classes and the test classes must be built and
available, the Gant script tests relay on being able to access the
Gant_Test class.

;;; Local Variables: ***
;;; fill-column: 78 ***
;;; End: ***
