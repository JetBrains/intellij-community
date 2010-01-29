#! /bin/bash
if [ -z "$WORK_IDEA_HOME" ]; then
   echo WORK_IDEA_HOME must be defined and point to build you're updating
   exit
fi

if [ -z "$DEV_IDEA_HOME" ]; then
   echo DEV_IDEA_HOME must be defined and point to source base your're updating from
   exit
fi

echo Updating $WORK_IDEA_HOME from compiled classes in $DEV_IDEA_HOME

rm -rf $WORK_IDEA_HOME/lib
rm -rf $WORK_IDEA_HOME/plugins

ant -f update.xml

cd $DEV_IDEA_HOME
cp -R $DEV_IDEA_HOME/out/deploy/* $WORK_IDEA_HOME

cd $WORK_IDEA_HOME/bin

