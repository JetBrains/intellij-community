#!/usr/bin/perl -w

use Mac::Finder::DSStore qw( writeDSDBEntries makeEntries );
use Mac::Memory qw( );
use Mac::Files qw( NewAliasMinimal );

$name = $ARGV[0];
$bg_pic = $ARGV[1];
$mountName = $ARGV[2];

&writeDSDBEntries("/Volumes/$mountName/.DS_Store",
    &makeEntries(".background", Iloc_xy => [ 560, 170 ]),
    &makeEntries(".DS_Store", Iloc_xy => [ 610, 170 ]),
    &makeEntries(".fseventsd", Iloc_xy => [ 660, 170 ]),
    &makeEntries(".Trashes", Iloc_xy => [ 710, 170 ]),
    &makeEntries(" ", Iloc_xy => [ 335, 120 ]),
    &makeEntries(".",
        BKGD_alias => NewAliasMinimal("/Volumes/$mountName/.background/$bg_pic"),
        ICVO => 1,
        fwi0_flds => [ 100, 400, 396, 855, "icnv", 0, 0 ],
        fwsw => 170,
        fwvh => 296,
        icvo => pack('A4 n A4 A4 n*', "icv4", 100, "none", "botm", 0, 0, 0, 0, 0, 1, 0, 100, 1),
        icvt => 12
    ),
    &makeEntries("$name.app", Iloc_xy => [ 110, 120 ])
);

