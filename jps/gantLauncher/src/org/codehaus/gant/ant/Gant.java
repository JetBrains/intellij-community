//  Gant -- A Groovy way of scripting Ant tasks.
//
//  Copyright © 2008-9 Russel Winder
//
//  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software distributed under the License is
//  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//  implied. See the License for the specific language governing permissions and limitations under the
//  License.

package org.codehaus.gant.ant ;

import java.io.File ;

import java.util.ArrayList ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;

import org.apache.tools.ant.AntClassLoader ;
import org.apache.tools.ant.BuildException ;
import org.apache.tools.ant.BuildListener ;
import org.apache.tools.ant.Project ;
import org.apache.tools.ant.Task ;

import org.codehaus.gant.GantBinding ;
import org.codehaus.gant.GantBuilder ;

/**
 *  Execute a Gant script.
 *
 *  <p>This Ant task provides a Gant calling capability. The original intention behind this was to support
 *  continuous integration systems that do not directly support Gant but only Ant.  However it also allows
 *  for gradual evolution of an Ant build into a Gant build.</p>
 *
 *  <p>Possible attributes are:</p>
 *
 *  <ul>
 *    <li>file &ndash; the path of the Gant script to execute.</li>
 *    <li>target &ndash; the target to execute; must be a single target name.  For specifying than a
 *        single target, use nested gantTarget tags.</li>
 *  </ul>
 *
 *  <p>Both of these are optional.  The file 'build.gant' and the default target are used by default.  An
 *  error results if there is no default target and no target is specified.</p>
 *
 *  <p>Definitions, if needed, are specified using nested <code>definition</code> tags, one for each symbol
 *  to be defined.  Each <code>definition</code> tag takes a compulsory <code>name</code> attribute and an
 *  optional <code>value</code> attribute.</p>
 *
 * @author Russel Winder
 */
public class Gant extends Task {
  /**
   *  The path to the file to use to drive the Gant build.  The default is build.gant.  This path is
   *  relative to the basedir of the Ant project if it is set, or the directory in which the job was started
   *  if the basedir is not set.
   */
  private String file = "build.gant" ;
  /**
   *  A class representing a nested definition tag.
   */
  public static final class Definition {
    private String name ;
    private String value ;
    public void setName ( final String s ) { name = s ; }
    public String getName ( ) { return name ; }
    public void setValue ( final String s ) { value = s ; }
    public String getValue ( ) { return value ; }
  }
  /**
   *  A list of definitions to be set in the Gant instance.
   */
  private final List<Definition> definitions = new ArrayList<Definition> ( ) ;
  /**
   *  A class representing a nested target tag.
   */
  public static final class GantTarget {
    private String value ;
    public void setValue ( final String s ) { value = s ; }
    public String getValue ( ) { return value ; }
  }
  /**
   *  A list of targets to be achieved by the Gant instance.
   */
  private final List<GantTarget> targets = new ArrayList<GantTarget> ( ) ;
  /**
   *  Set the name of the build file to use.  This path is relative to the basedir of the Ant project if it
   *  is set, or the directory in which the job was started if the basedir is not set.
   *
   *  @param f The name of the file to be used to drive the build.
   */
  public void setFile ( final String f ) { file = f ; }
  /**
   *  Set the target to be achieved.
   *
   *  @param t The target to achieve.
   */
  public void setTarget ( final String t ) {
    final GantTarget gt = new GantTarget ( ) ;
    gt.setValue ( t ) ;
    targets.add ( gt ) ;
  }
  /**
   *  Create a node to represent a nested <code>gantTarget</code> tag.
   *
   *  @return a new <code>GantTarget</code> instance ready for values to be added.
   */
  public GantTarget createGantTarget ( ) {
    final GantTarget gt = new GantTarget ( ) ;
    targets.add ( gt ) ;
    return gt ;
  }
  /**
   *  Create a node to represent a nested <code>definition</code> tag.
   *
   *  @return a new <code>Definition</code> instance ready for values to be added.
   */
  public Definition createDefinition ( ) {
    final Definition definition = new Definition ( ) ;
    definitions.add ( definition ) ;
    return definition ;
  }
  /**
   * Load the file and then execute it.
   */
  @Override public void execute ( ) throws BuildException {
    //
    //  At first it might seem appropriate to use the Project object from the calling Ant instance as the
    //  Project object used by the AntBuilder object and hence GantBuilder object associated with the Gant
    //  instance we are going to create here.  However, if we just use that Project object directly then
    //  there are problems with proper annotation of the lines of output, so it isn't really an option.
    //  Therefore create a new Project instance and set the things appropriately from the original Project
    //  object.
    //
    //  Issues driving things here are GANT-50 and GANT-80.  GANT-50 is about having the correct base
    //  directory for operations, GANT-80 is about ensuring that all output generation actually generated
    //  observable output.
    //
    //  NB As this class is called Gant, we have to use fully qualified name to get to the Gant main class.
    //
    final Project antProject =  getOwningTarget ( ).getProject ( ) ;
    final Project newProject = new Project ( ) ;
    newProject.init ( ) ;
    //  Deal with GANT-80 by getting all the the loggers from the Ant instance Project object and adding
    //  them to the new Project Object.  This was followed up by GANT-91 so the code was amended to copying
    //  over all listeners except the class loader if present.
    for ( final Object o : antProject.getBuildListeners ( ) ) {
      final BuildListener listener = (BuildListener) o ;
      if ( ! ( listener instanceof AntClassLoader ) ) { newProject.addBuildListener ( listener ) ; }
    }
    //  Deal with GANT-50 by getting the base directory from the Ant instance Project object and use it for
    //  the new Project object.
    newProject.setBaseDir ( antProject.getBaseDir ( ) ) ;
    final File gantFile = newProject.resolveFile( file ) ;
    if ( ! gantFile.exists ( ) ) { throw new BuildException ( "Gantfile does not exist." , getLocation ( ) ) ; }
    final GantBuilder ant = new GantBuilder ( newProject ) ;
    final Map<String,String> environmentParameter = new HashMap<String,String> ( ) ;
    environmentParameter.put ( "environment" , "environment" ) ;
    ant.invokeMethod ( "property" , new Object[] { environmentParameter } ) ;
    final GantBinding binding = new GantBinding ( ) ;
    binding.forcedSettingOfVariable ( "ant" , ant ) ;
    for ( final Definition definition : definitions ) {
      final Map<String,String> definitionParameter = new HashMap<String,String> ( ) ;
      definitionParameter.put ( "name" , definition.getName ( ) ) ;
      definitionParameter.put ( "value" , definition.getValue ( ) ) ;
      ant.invokeMethod ( "property" , new Object[] { definitionParameter } ) ;
    }
    final gant.Gant gant = new gant.Gant ( binding ) ;
    gant.loadScript ( gantFile ) ;
    final List<String> targetsAsStrings = new ArrayList<String> ( ) ;
    for ( final GantTarget g : targets ) { targetsAsStrings.add ( g.getValue ( ) ) ; }
    final int returnCode =  gant.processTargets ( targetsAsStrings ) ;
    if ( returnCode != 0 ) { throw new BuildException ( "Gant execution failed with return code " + returnCode + '.' , getLocation ( ) ) ; }
  }
}
